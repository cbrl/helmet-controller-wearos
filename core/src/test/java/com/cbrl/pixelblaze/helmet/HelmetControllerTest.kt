package com.cbrl.pixelblaze.helmet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class HelmetControllerTest {
    private class FakeLink : HelmetLink {
        val replies = MutableSharedFlow<JSONObject>(replay = 1, extraBufferCapacity = 16)
        override val vars: Flow<JSONObject> = replies
        val writes = mutableListOf<JSONObject>()
        override fun setVars(vars: JSONObject) {
            writes += vars
        }
    }

    private val helmetState = HelmetState.Default
        .with(HelmetSegment.LeftEar, SegmentState(effect = HelmetEffect.Breathe, hue = 0.3f))

    /** Attaches to a helmet that another app already restored (session 77). */
    private suspend fun TestScope.attached(state: HelmetState = helmetState): Pair<HelmetController, FakeLink> {
        val controller = HelmetController(backgroundScope, Random(1))
        val link = FakeLink()
        link.replies.emit(patternVars(state, session = 77))
        controller.attach(link, restore = null)
        runCurrent()
        return controller to link
    }

    @Test
    fun attach_restoresTheSavedStateAfterAPatternRestart() = runTest {
        val controller = HelmetController(backgroundScope, Random(1))
        val link = FakeLink()
        link.replies.emit(patternVars(HelmetState.Default, session = 0))
        val saved = helmetState.copy(message = "HI", blackout = true)

        controller.attach(link, restore = saved)
        runCurrent()

        assertEquals(saved, controller.state.value)
        val written = link.writes.single()
        assertTrue(written.getInt(HelmetProtocol.VAR_SESSION) in 1..HelmetProtocol.MAX_COUNTER)
        assertEquals(HelmetEffect.Breathe.id, written.getJSONArray("z_5").getInt(HelmetProtocol.FIELD_EFFECT))
        assertEquals(2, written.getInt(HelmetProtocol.VAR_MESSAGE_LENGTH))
        assertEquals(1, written.getInt(HelmetProtocol.VAR_BLACKOUT))
    }

    @Test
    fun attach_adoptsTheHelmetStateWhenAnotherAppRestoredIt() = runTest {
        val (controller, link) = attached()

        assertEquals(helmetState, controller.state.value)
        assertTrue("no write when the helmet state is adopted", link.writes.isEmpty())
    }

    @Test
    fun attach_failsForAnIncompatiblePattern() = runTest {
        val controller = HelmetController(backgroundScope)
        val link = FakeLink()
        link.replies.emit(patternVars(HelmetState.Default, session = 1).put(HelmetProtocol.VAR_VERSION, 3))
        try {
            controller.attach(link, restore = null)
            fail("attach must fail")
        } catch (expected: PatternIncompatibleException) {
            assertTrue(expected.message!!.contains("v3"))
        }
        assertNull(controller.state.value)
    }

    @Test
    fun changes_updateStateAtOnceAndConflateWrites() = runTest {
        val (controller, link) = attached()

        controller.setSegmentBrightness(HelmetSegment.Visor, 0.2f)
        controller.setSegmentBrightness(HelmetSegment.Visor, 0.4f)
        controller.setSegmentBrightness(HelmetSegment.Visor, 0.6f)
        assertEquals(0.6f, controller.state.value!![HelmetSegment.Visor].brightness, 0f)

        runCurrent()
        val first = link.writes.single()
        assertEquals(setOf("z_0"), first.keyNames())
        assertEquals(0.6, first.getJSONArray("z_0").getDouble(HelmetProtocol.FIELD_BRI), 1e-6)

        controller.setSegmentBrightness(HelmetSegment.Visor, 0.8f)
        runCurrent()
        assertEquals("the next write waits for the flush interval", 1, link.writes.size)
        advanceTimeBy(HelmetController.FLUSH_INTERVAL_MILLIS + 1)
        assertEquals(2, link.writes.size)
    }

    @Test
    fun replies_areIgnoredWhileAWriteWaits() = runTest {
        val (controller, link) = attached()

        link.replies.emit(patternVars(helmetState, session = 77))  // an old reply arrives...
        controller.setSegmentEnabled(HelmetSegment.RightEar, false)  // ...while a write waits
        runCurrent()

        assertFalse(controller.state.value!![HelmetSegment.RightEar].on)
    }

    @Test
    fun replies_showChangesFromOtherApps() = runTest {
        val (controller, link) = attached()
        val changed = helmetState.with(HelmetSegment.Visor, SegmentState(effect = HelmetEffect.Snake))

        link.replies.emit(patternVars(changed, session = 77))
        runCurrent()

        assertEquals(changed, controller.state.value)
        assertTrue(link.writes.isEmpty())
    }

    @Test
    fun aPatternRestartWhileConnectedRestoresTheCurrentState() = runTest {
        val (controller, link) = attached()

        link.replies.emit(patternVars(HelmetState.Default, session = 0))
        runCurrent()

        assertEquals(helmetState, controller.state.value)
        val written = link.writes.single()
        assertTrue(written.getInt(HelmetProtocol.VAR_SESSION) > 0)
        assertEquals(HelmetEffect.Breathe.id, written.getJSONArray("z_5").getInt(HelmetProtocol.FIELD_EFFECT))
    }

    @Test
    fun masterAndBlackout_writeOnlyMasterVariables() = runTest {
        val (controller, link) = attached()

        controller.setBlackout(true)
        controller.setMaster(0.5f)
        runCurrent()

        val written = link.writes.single()
        assertEquals(setOf(HelmetProtocol.VAR_MASTER, HelmetProtocol.VAR_BLACKOUT), written.keyNames())
        assertEquals(1, written.getInt(HelmetProtocol.VAR_BLACKOUT))
        assertEquals(helmetState.segments, controller.state.value!!.segments)
    }

    @Test
    fun setMessage_advancesTheRevision() = runTest {
        val (controller, link) = attached()

        controller.setMessage("NEW")
        runCurrent()

        val written = link.writes.single()
        assertEquals(3, written.getInt(HelmetProtocol.VAR_MESSAGE_LENGTH))
        assertEquals(1, written.getInt(HelmetProtocol.VAR_MESSAGE_REV))
        assertEquals("NEW", controller.state.value!!.message)
    }

    @Test
    fun matrixEffects_areIgnoredOnStrips() = runTest {
        val (controller, link) = attached()

        controller.setSegmentEffect(HelmetSegment.VisorSide1, HelmetEffect.Text)
        controller.copyAppearanceToAll(HelmetSegment.Visor)
        runCurrent()

        val state = controller.state.value!!
        assertEquals(HelmetEffect.Scanner, state[HelmetSegment.Visor].effect)
        assertEquals(HelmetEffect.RainbowDown, state[HelmetSegment.VisorSide1].effect)
        assertEquals(0.15f, state[HelmetSegment.VisorSide1].brightness, 0f)
        assertFalse("the visor is unchanged, so it is not written", link.writes.single().has("z_0"))
    }

    @Test
    fun applyLook_keepsMasterAndBlackout() = runTest {
        val (controller, link) = attached(helmetState.copy(master = 0.4f, blackout = true))
        val look = HelmetState.Default.copy(message = "PRESET").look

        controller.applyLook(look)
        runCurrent()

        val state = controller.state.value!!
        assertEquals(look, state.look)
        assertEquals(0.4f, state.master, 0f)
        assertTrue(state.blackout)
        assertFalse(link.writes.single().has(HelmetProtocol.VAR_MASTER))
    }

    @Test
    fun detach_stopsWrites() = runTest {
        val (controller, link) = attached()

        controller.detach()
        controller.setSegmentBrightness(HelmetSegment.Visor, 0.9f)
        runCurrent()

        assertTrue(link.writes.isEmpty())
        assertEquals("the last state stays for display", helmetState, controller.state.value)
    }

    @Test
    fun pendingWrites_mergeByNameAndTrackInFlightBatches() {
        val pending = PendingWrites()
        assertTrue(pending.isIdle())

        pending.put(mapOf("a" to 1, "b" to 2))
        pending.put(mapOf("a" to 3))
        assertFalse(pending.isIdle())

        assertEquals(mapOf("a" to 3, "b" to 2), pending.take())
        assertFalse("a taken batch is in flight", pending.isIdle())
        pending.sent()
        assertTrue(pending.isIdle())
        assertTrue(pending.take().isEmpty())
        assertTrue(pending.isIdle())
    }
}
