package com.cbrl.pixelblaze.device

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PixelblazeProtocolTest {
    @Test
    fun programListAccumulator_combinesFragmentsUntilEndFlag() {
        val accumulator = ProgramListAccumulator()

        assertFalse(accumulator.accept(programFrame(0x01, "id-a\tAlpha\nid-b\tBe")))
        assertTrue(accumulator.accept(programFrame(0x04, "ta\n")))

        assertEquals(
            mapOf(
                "Alpha" to PatternInfo("Alpha", "id-a"),
                "Beta" to PatternInfo("Beta", "id-b"),
            ),
            accumulator.patterns(),
        )
    }

    @Test
    fun programListAccumulator_acceptsSingleFrameAndIgnoresOtherBinaryTypes() {
        val accumulator = ProgramListAccumulator()

        assertFalse(accumulator.accept(byteArrayOf(0x09, 0x05, 0x00)))
        assertTrue(accumulator.accept(programFrame(0x05, "helmet-id\tHelmet\n")))
        assertEquals("helmet-id", accumulator.patterns().getValue("Helmet").id)
    }

    @Test
    fun beaconParser_usesActualLengthTypeAndLittleEndianId() {
        val valid = ByteBuffer.allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(PixelblazeDiscovery.BEACON_ID)
            .putInt(0x646B0E)
            .putInt(0)
            .array()

        val endpoint = PixelblazeDiscovery.parseBeacon(valid, "192.168.1.20")
        assertEquals(PixelblazeEndpoint(0x646B0E, "192.168.1.20"), endpoint)
        assertEquals("646B0E", endpoint?.displayId)
        assertNull(PixelblazeDiscovery.parseBeacon(valid.copyOf(8), "192.168.1.20"))

        valid[0] = 0
        assertNull(PixelblazeDiscovery.parseBeacon(valid, "192.168.1.20"))
    }

    @Test
    fun endpoint_sameDeviceIgnoresNetwork() {
        val a = PixelblazeEndpoint(7, "10.0.0.2")
        assertTrue(a.sameDevice(PixelblazeEndpoint(7, "10.0.0.2")))
        assertFalse(a.sameDevice(PixelblazeEndpoint(7, "10.0.0.3")))
        assertFalse(a.sameDevice(null))
        assertEquals("?", PixelblazeEndpoint(PixelblazeEndpoint.UNKNOWN_ID, "10.0.0.2").displayId)
    }

    @Test
    fun varsReadTracker_dropsRepliesToReadsSentBeforeAWrite() {
        val tracker = VarsReadTracker()

        tracker.onRead()   // read 1, before the write
        tracker.onWrite()
        tracker.onRead()   // read 2, after the write

        assertFalse("reply 1 does not contain the write", tracker.onReply())
        assertTrue("reply 2 contains the write", tracker.onReply())
        assertFalse("a reply without a request is ignored", tracker.onReply())
    }

    @Test
    fun varsReadTracker_acceptsRepliesWhenNoWriteHappened() {
        val tracker = VarsReadTracker()
        tracker.onRead()
        tracker.onRead()
        assertEquals(2, tracker.pendingReads())
        assertTrue(tracker.onReply())
        assertTrue(tracker.onReply())
        assertEquals(0, tracker.pendingReads())
    }

    @Test
    fun varsReadTracker_boundsPendingReads() {
        val tracker = VarsReadTracker(maxPending = 2)
        repeat(5) { tracker.onRead() }
        assertEquals(2, tracker.pendingReads())
    }

    @Test
    fun pixelblazeInfo_readsConfigReplyOnly() {
        val config = JSONObject()
            .put("name", "Pixelblaze_646B0E")
            .put("ver", "3.70")
            .put("pixelCount", 496)
            .put("chipId", 6581006)
        assertEquals(
            PixelblazeInfo(name = "Pixelblaze_646B0E", firmware = "3.70", pixelCount = 496, chipId = 6581006),
            PixelblazeInfo.fromConfig(config),
        )
        assertNull(PixelblazeInfo.fromConfig(JSONObject().put("fps", 30.0)))
    }

    private fun programFrame(flags: Int, payload: String): ByteArray =
        byteArrayOf(0x07, flags.toByte()) + payload.encodeToByteArray()
}
