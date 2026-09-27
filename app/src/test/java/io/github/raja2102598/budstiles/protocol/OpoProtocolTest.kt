package io.github.raja2102598.budstiles.protocol

import io.github.raja2102598.budstiles.protocol.OpoProtocol.Frame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Received frames below are verbatim captures from OnePlus Buds 3. */
class OpoProtocolTest {

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun frame(s: String) = OpoProtocol.FrameParser().feed(hex(s)).single()

    /** Compares a built packet with [expected], ignoring the sequence byte. */
    private fun assertPacket(expected: String, actual: ByteArray) {
        val want = hex(expected)
        assertEquals(want.size, actual.size)
        actual[6] = want[6]
        assertArrayEquals(want, actual)
    }

    @Test fun `builds handshake`() =
        assertPacket("AA 07 00 00 00 01 00 00 00", OpoProtocol.handshake())

    @Test fun `builds event registration`() =
        assertPacket("AA 0B 00 00 05 02 00 04 00 03 01 02 03", OpoProtocol.registerEvents())

    @Test fun `builds set-mode packets`() {
        assertPacket(
            "AA 0A 00 00 04 04 00 03 00 01 01 04",
            OpoProtocol.setNoiseMode(NoiseMode.TRANSPARENCY),
        )
        assertPacket("AA 0A 00 00 04 04 00 03 00 01 01 01", OpoProtocol.setNoiseMode(NoiseMode.OFF))
        assertPacket(
            "AA 0A 00 00 04 04 00 03 00 01 01 10",
            OpoProtocol.setNoiseMode(NoiseMode.NOISE_CANCELLING, AncLevel.DEEP),
        )
        assertPacket(
            "AA 0B 00 00 04 04 00 04 00 01 01 00 08",
            OpoProtocol.setNoiseMode(NoiseMode.NOISE_CANCELLING, AncLevel.ADAPTIVE),
        )
    }

    @Test fun `sequence numbers advance`() {
        val a = OpoProtocol.handshake()[6]
        val b = OpoProtocol.handshake()[6]
        assertTrue(a != b)
    }

    @Test fun `parser splits concatenated frames and skips garbage`() {
        val stream = hex(
            "00 13 " +
                "AA 08 00 00 04 84 04 01 00 00 " +
                "AA 0C 00 00 04 02 FF 05 00 03 01 01 00 01",
        )
        val frames = OpoProtocol.FrameParser().feed(stream)
        assertEquals(listOf(0x8404, 0x0204), frames.map { it.cmd })
    }

    @Test fun `parser waits for a frame split across reads`() {
        val parser = OpoProtocol.FrameParser()
        val bytes = hex("AA 0C 00 00 04 02 FF 05 00 03 01 01 10 00")
        assertTrue(parser.feed(bytes.copyOfRange(0, 6)).isEmpty())
        val frames = parser.feed(bytes.copyOfRange(6, bytes.size))
        assertEquals(NoiseMode.NOISE_CANCELLING, OpoProtocol.parseNoiseMode(frames.single()))
    }

    @Test fun `parses noise-mode query reply`() =
        assertEquals(
            NoiseMode.OFF,
            OpoProtocol.parseNoiseMode(frame("AA 0C 00 00 0C 81 03 05 00 00 01 01 08 00")),
        )

    @Test fun `parses noise-mode events`() {
        assertEquals(
            NoiseMode.TRANSPARENCY,
            OpoProtocol.parseNoiseMode(frame("AA 0C 00 00 04 02 FF 05 00 03 01 01 00 01")),
        )
        assertEquals(
            NoiseMode.OFF,
            OpoProtocol.parseNoiseMode(frame("AA 0C 00 00 04 02 FF 05 00 03 01 01 08 00")),
        )
        assertEquals(
            NoiseMode.NOISE_CANCELLING,
            OpoProtocol.parseNoiseMode(frame("AA 0C 00 00 04 02 FF 05 00 03 01 01 10 00")),
        )
    }

    @Test fun `ignores unrelated events`() {
        // Same event subtype, different field (not the mode report).
        assertNull(OpoProtocol.parseNoiseMode(frame("AA 0C 00 00 04 02 FF 05 00 03 02 01 02 00")))
        // Wearing-status event.
        val wearing = frame("AA 0F 00 00 04 02 18 08 00 02 03 01 05 02 05 03 04")
        assertNull(OpoProtocol.parseNoiseMode(wearing))
        assertNull(OpoProtocol.parseBattery(wearing))
    }

    @Test fun `parses battery reply`() {
        val battery = OpoProtocol.parseBattery(
            frame("AA 0F 00 00 06 81 F0 08 00 00 03 01 14 02 14 03 01"),
        )!!
        assertEquals(Charge(20, false), battery.left)
        assertEquals(Charge(20, false), battery.right)
        assertEquals(Charge(1, false), battery.case)
    }

    @Test fun `parses charging flag`() {
        val battery = OpoProtocol.parseBattery(Frame(0x0204, hex("01 01 03 C8")))!!
        assertEquals(Charge(72, true), battery.case)
    }

    @Test fun `recognises successful ack`() {
        assertTrue(OpoProtocol.isSuccessAck(frame("AA 08 00 00 04 84 04 01 00 00")))
        assertFalse(OpoProtocol.isSuccessAck(frame("AA 08 00 00 04 84 04 01 00 01")))
    }
}
