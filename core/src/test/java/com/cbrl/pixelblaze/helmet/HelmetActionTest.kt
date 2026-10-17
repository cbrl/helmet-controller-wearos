package com.cbrl.pixelblaze.helmet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelmetActionTest {
    @Test
    fun tokensRoundTrip() {
        listOf(
            HelmetAction.SetBlackout(true),
            HelmetAction.SetBlackout(false),
            HelmetAction.LoadPreset("4f1c-preset:id"),
        ).forEach { assertEquals(it, HelmetAction.fromToken(it.toToken())) }
    }

    @Test
    fun badTokensGiveNoAction() {
        listOf(null, "", "blackout", "blackout:maybe", "preset:", "preset:  ", "reboot").forEach {
            assertNull(it, HelmetAction.fromToken(it))
        }
    }

    private class Fixture(scope: TestScope, ready: Boolean) {
        val ready = MutableStateFlow(ready)
        val performed = mutableListOf<HelmetAction>()
        val queue = ActionQueue(scope.backgroundScope, this.ready) { action ->
            performed += action
            ActionResult(action, success = true, message = "Done")
        }
    }

    @Test
    fun runsAtOnceWhenReady() = runTest {
        val fixture = Fixture(this, ready = true)
        val action = HelmetAction.SetBlackout(true)
        fixture.queue.submit(action)
        runCurrent()
        assertEquals(listOf<HelmetAction>(action), fixture.performed)
        assertNull(fixture.queue.waiting.value)
        assertEquals(ActionResult(action, true, "Done"), fixture.queue.result.value)
    }

    @Test
    fun waitsForTheHelmet() = runTest {
        val fixture = Fixture(this, ready = false)
        val action = HelmetAction.LoadPreset("a")
        fixture.queue.submit(action)
        advanceTimeBy(ActionQueue.TIMEOUT_MILLIS / 2)
        assertEquals(action, fixture.queue.waiting.value)
        assertTrue(fixture.performed.isEmpty())

        fixture.ready.value = true
        runCurrent()
        assertEquals(listOf<HelmetAction>(action), fixture.performed)
        assertNull(fixture.queue.waiting.value)
    }

    @Test
    fun failsAfterTheTimeout() = runTest {
        val fixture = Fixture(this, ready = false)
        val action = HelmetAction.SetBlackout(false)
        fixture.queue.submit(action)
        advanceTimeBy(ActionQueue.TIMEOUT_MILLIS + 1)
        assertTrue(fixture.performed.isEmpty())
        assertNull(fixture.queue.waiting.value)
        val result = fixture.queue.result.value!!
        assertFalse(result.success)
        assertEquals(ActionQueue.NOT_READY_MESSAGE, result.message)

        // The helmet connects later: the dropped action does not run.
        fixture.ready.value = true
        runCurrent()
        assertTrue(fixture.performed.isEmpty())
    }

    @Test
    fun aNewActionReplacesTheWaitingAction() = runTest {
        val fixture = Fixture(this, ready = false)
        fixture.queue.submit(HelmetAction.SetBlackout(true))
        runCurrent()
        val newer = HelmetAction.LoadPreset("b")
        fixture.queue.submit(newer)
        assertEquals(newer, fixture.queue.waiting.value)

        fixture.ready.value = true
        runCurrent()
        assertEquals(listOf<HelmetAction>(newer), fixture.performed)
    }

    @Test
    fun clearResultHidesTheResult() = runTest {
        val fixture = Fixture(this, ready = true)
        fixture.queue.submit(HelmetAction.SetBlackout(true))
        runCurrent()
        fixture.queue.clearResult()
        assertNull(fixture.queue.result.value)
    }
}
