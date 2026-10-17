package com.cbrl.pixelblaze.presentation

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
            .putInt(DeviceLocator.BEACON_ID)
            .putInt(0x1234ABCD)
            .putInt(0)
            .array()

        assertEquals(
            PixelblazeDevice("192.168.1.20", 0x1234ABCD),
            DeviceLocator.parseBeacon(valid, "192.168.1.20"),
        )
        assertNull(DeviceLocator.parseBeacon(valid.copyOf(8), "192.168.1.20"))

        valid[0] = 0
        assertNull(DeviceLocator.parseBeacon(valid, "192.168.1.20"))
    }

    private fun programFrame(flags: Int, payload: String): ByteArray =
        byteArrayOf(0x07, flags.toByte()) + payload.encodeToByteArray()
}
