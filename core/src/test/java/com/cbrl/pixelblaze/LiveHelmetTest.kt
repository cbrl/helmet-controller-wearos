package com.cbrl.pixelblaze

import com.cbrl.pixelblaze.device.PixelblazeClient
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.device.PixelblazeSession
import com.cbrl.pixelblaze.helmet.HelmetController
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.HelmetLink
import com.cbrl.pixelblaze.helmet.HelmetProtocol
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.SegmentState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the core against a real Pixelblaze that runs the Helmet v4 pattern. The test changes the
 * helmet for about 15 seconds, then restores the state that it found.
 *
 * Run: PIXELBLAZE_HOST=192.168.0.157 ./gradlew :core:testDebugUnitTest --tests '*LiveHelmetTest*'
 */
class LiveHelmetTest {
    private fun HelmetProtocol.Decoded.snapshot(): HelmetProtocol.Snapshot =
        (this as? HelmetProtocol.Decoded.Valid)?.snapshot ?: error("Not a valid v4 reply: $this")

    private var writes = 0

    private fun PixelblazeSession.link(): HelmetLink {
        val session = this
        return object : HelmetLink {
            override val vars: Flow<JSONObject> = session.vars
            override fun setVars(vars: JSONObject) {
                writes++
                session.setVars(vars)
            }
        }
    }

    private suspend fun PixelblazeSession.awaitSnapshot(
        predicate: (HelmetProtocol.Snapshot) -> Boolean,
    ): HelmetProtocol.Snapshot = withTimeout(6_000L) {
        vars.first { predicate(HelmetProtocol.decode(it).snapshot()) }.let { HelmetProtocol.decode(it).snapshot() }
    }

    private fun assertClose(expected: SegmentState, actual: SegmentState) {
        assertEquals(expected.on, actual.on)
        assertEquals(expected.effect, actual.effect)
        assertEquals(expected.hue, actual.hue, 1e-3f)
        assertEquals(expected.saturation, actual.saturation, 1e-3f)
        assertEquals(expected.brightness, actual.brightness, 1e-3f)
        assertEquals(expected.speed, actual.speed, 1e-3f)
        assertEquals(expected.fadeSeconds, actual.fadeSeconds, 1e-3f)
    }

    @Test
    fun liveHelmet() = runBlocking {
        val host = System.getenv("PIXELBLAZE_HOST")
        assumeTrue("Set PIXELBLAZE_HOST to run the live test", !host.isNullOrBlank())

        withTimeout(90_000L) {
            PixelblazeClient.connect(PixelblazeEndpoint(PixelblazeEndpoint.UNKNOWN_ID, host!!)) { session ->
                val active = session.awaitActiveProgram()
                println("active program: $active")
                assertTrue(HelmetProtocol.PATTERN_NAME in session.programs())
                val info = session.info.filterNotNull().first()
                println("device: $info")
                assertEquals(496, info.pixelCount)

                val original = HelmetProtocol.decode(session.vars.first()).snapshot()
                println("original session ${original.session}, rev ${original.messageRev}, layout ${original.layout}")

                coroutineScope {
                    // 1. A restarted pattern (__session 0) gets the saved state.
                    session.setVars(JSONObject().put(HelmetProtocol.VAR_SESSION, 0))
                    session.awaitSnapshot { it.session == 0 }
                    val saved = original.state
                        .with(HelmetSegment.Visor, SegmentState(hue = 0.33f, brightness = 0.1f, effect = HelmetEffect.SolidColor))
                        .with(HelmetSegment.LeftEar, SegmentState(hue = 0.6f, saturation = 0.9f, effect = HelmetEffect.Breathe, speed = 2f, fadeSeconds = 0.5f))
                        .copy(message = "V4 TEST")
                    val controller = HelmetController(this)
                    controller.attach(session.link(), restore = saved)
                    val restored = session.awaitSnapshot { it.session != 0 }
                    println("restored with session token ${restored.session}")
                    HelmetSegment.entries.forEach { assertClose(saved[it], restored.state[it]) }
                    assertEquals("V4 TEST", restored.state.message)

                    // 2. A fast drag sends the latest value, and the state never jumps back.
                    val seen = mutableListOf<Float>()
                    val watcher = launch {
                        controller.state.filterNotNull().collect { seen += it[HelmetSegment.Visor].brightness }
                    }
                    for (step in 1..20) {
                        controller.setSegmentBrightness(HelmetSegment.Visor, 0.1f + step * 0.01f)
                        delay(10L)
                    }
                    session.awaitSnapshot { kotlin.math.abs(it.state[HelmetSegment.Visor].brightness - 0.3f) < 1e-3f }
                    delay(2_500L)  // let several polls arrive
                    watcher.cancel()
                    val afterDrag = seen.dropWhile { it < 0.3f - 1e-3f }
                    println("brightness values seen: ${seen.size}, after the drag: ${afterDrag.map { "%.4f".format(it) }.distinct()}")
                    assertTrue("state must not jump back after the drag", afterDrag.all { kotlin.math.abs(it - 0.3f) < 1e-3f })
                    assertTrue("state must not jump back during the drag", seen.zipWithNext().all { (a, b) -> b >= a - 1e-3f })

                    // 3. Message and revision
                    controller.setMessage("HELLO V4")
                    val message = session.awaitSnapshot { it.state.message == "HELLO V4" }
                    assertEquals(restored.messageRev + 1, message.messageRev)

                    // 4. Blackout keeps the segment state.
                    controller.setBlackout(true)
                    val dark = session.awaitSnapshot { it.state.blackout }
                    assertClose(controller.state.value!![HelmetSegment.LeftEar], dark.state[HelmetSegment.LeftEar])
                    controller.setBlackout(false)
                    session.awaitSnapshot { !it.state.blackout }

                    // 5. A second app adopts the helmet state and writes nothing.
                    val expected = controller.state.value!!
                    controller.detach()
                    val second = HelmetController(this)
                    val writesBefore = writes
                    second.attach(session.link(), restore = HelmetState.Default)
                    delay(1_500L)
                    assertEquals("the second app adopts and writes nothing", writesBefore, writes)
                    assertEquals(expected.message, second.state.value!!.message)
                    HelmetSegment.entries.forEach { assertClose(expected[it], second.state.value!![it]) }
                    second.detach()
                }

                // Put back the state that the test found.
                session.setVars(
                    JSONObject(HelmetProtocol.stateVars(original.state, HelmetProtocol.nextCounter(original.messageRev)))
                        .put(HelmetProtocol.VAR_SESSION, original.session),
                )
                session.awaitSnapshot { it.session == original.session && it.state.message == original.state.message }
                println("fps after test: ${session.fps.value}")
            }
        }
    }
}
