package io.github.raja2102598.budstiles.protocol

import java.util.UUID

/**
 * The OPO control protocol spoken by OnePlus, OPPO and realme earbuds over a
 * classic-Bluetooth RFCOMM channel. See docs/PROTOCOL.md for the full write-up.
 *
 * Frame layout (little-endian, no checksum):
 *
 *     AA  len  00 00  cmdLo cmdHi  seq  plenLo plenHi  payload…
 *
 * where `len` counts the bytes after itself (7 + payload length).
 * Replies use the request's command with bit 15 set (0x0404 -> 0x8404).
 */
object OpoProtocol {

    /** RFCOMM service UUIDs, most likely first. */
    val SERVICE_UUIDS: List<UUID> = listOf(
        UUID.fromString("0000079A-D102-11E1-9B23-00025B00A5A5"),
        UUID.fromString("00001107-D102-11E1-9B23-00025B00A5A5"),
    )

    /** Raw RFCOMM channel to try when SDP lookup fails. */
    const val FALLBACK_CHANNEL = 15

    const val CMD_HANDSHAKE = 0x0100
    const val CMD_QUERY_BATTERY = 0x0106
    const val CMD_QUERY_NOISE_MODE = 0x010C
    const val CMD_EVENT = 0x0204
    const val CMD_REGISTER_EVENTS = 0x0205
    const val CMD_SET_NOISE_MODE = 0x0404

    private const val SOF = 0xAA
    private const val HEADER_SIZE = 9

    private const val EVENT_BATTERY = 0x01
    private const val EVENT_WEARING = 0x02
    private const val EVENT_NOISE_MODE = 0x03

    // Set-mode masks. Reports use a different bit table, see parseNoiseMode().
    private val SET_OFF = byteArrayOf(0x01)
    private val SET_TRANSPARENCY = byteArrayOf(0x04)

    private var sequence = 1

    fun responseTo(cmd: Int) = cmd or 0x8000

    fun handshake() = packet(CMD_HANDSHAKE)

    /** Subscribe to battery, wearing and noise-mode change events. */
    fun registerEvents() =
        packet(CMD_REGISTER_EVENTS, bytes(3, EVENT_BATTERY, EVENT_WEARING, EVENT_NOISE_MODE))

    fun queryNoiseMode() = packet(CMD_QUERY_NOISE_MODE, bytes(0x01, 0x01))

    fun queryBattery() = packet(CMD_QUERY_BATTERY)

    fun setNoiseMode(mode: NoiseMode, level: AncLevel = AncLevel.DEEP): ByteArray {
        val mask = when (mode) {
            NoiseMode.OFF -> SET_OFF
            NoiseMode.TRANSPARENCY -> SET_TRANSPARENCY
            NoiseMode.NOISE_CANCELLING -> level.mask
        }
        return packet(CMD_SET_NOISE_MODE, bytes(0x01, 0x01) + mask)
    }

    @Synchronized
    fun packet(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val seq = sequence
        sequence = if (sequence >= 0xFE) 1 else sequence + 1
        return ByteArray(HEADER_SIZE + payload.size).also {
            it[0] = SOF.toByte()
            it[1] = (7 + payload.size).toByte()
            it[4] = cmd.toByte()
            it[5] = (cmd shr 8).toByte()
            it[6] = seq.toByte()
            it[7] = payload.size.toByte()
            it[8] = (payload.size shr 8).toByte()
            payload.copyInto(it, HEADER_SIZE)
        }
    }

    /** A decoded frame. */
    class Frame(val cmd: Int, val payload: ByteArray)

    /**
     * Splits a byte stream into frames. Feed it whatever the socket returns;
     * partial frames are kept until the rest arrives, garbage is skipped.
     */
    class FrameParser {
        private var buffer = ByteArray(0)

        fun feed(data: ByteArray, count: Int = data.size): List<Frame> {
            buffer += data.copyOf(count)
            val frames = ArrayList<Frame>()
            var i = 0
            while (i < buffer.size) {
                if (buffer[i].u() != SOF) { i++; continue }
                if (i + 1 >= buffer.size) break
                val end = i + 2 + buffer[i + 1].u()
                if (end > buffer.size) break
                if (end - i >= HEADER_SIZE) {
                    val cmd = buffer[i + 4].u() or (buffer[i + 5].u() shl 8)
                    frames += Frame(cmd, buffer.copyOfRange(i + HEADER_SIZE, end))
                }
                i = end
            }
            buffer = buffer.copyOfRange(i, buffer.size)
            return frames
        }
    }

    /**
     * The noise mode a frame reports, or null if it carries none. Reports come
     * as a query reply (`status 01 01 mask16`) or an event (`03 01 01 mask16`).
     * The report bits differ from the set bits: 0x0008 = off,
     * 0x0100 = transparency, anything else = noise cancelling.
     */
    fun parseNoiseMode(frame: Frame): NoiseMode? {
        val p = frame.payload
        val isReport = when (frame.cmd) {
            responseTo(CMD_QUERY_NOISE_MODE) -> p.size >= 5 && p[0].u() == 0x00
            CMD_EVENT -> p.size >= 5 && p[0].u() == EVENT_NOISE_MODE
            else -> false
        }
        if (!isReport || p[1].u() != 0x01 || p[2].u() != 0x01) return null
        return when (p[3].u() or (p[4].u() shl 8)) {
            0x0008 -> NoiseMode.OFF
            0x0100 -> NoiseMode.TRANSPARENCY
            0 -> null
            else -> NoiseMode.NOISE_CANCELLING
        }
    }

    /**
     * Battery levels from a query reply (`00 count (id level)…`) or a battery
     * event (`01 count (id level)…`). id 1/2/3 = left/right/case; level bit 7
     * means charging.
     */
    fun parseBattery(frame: Frame): Battery? {
        val p = frame.payload
        val isBattery = when (frame.cmd) {
            responseTo(CMD_QUERY_BATTERY) -> p.size >= 2 && p[0].u() == 0x00
            CMD_EVENT -> p.size >= 2 && p[0].u() == EVENT_BATTERY
            else -> false
        }
        if (!isBattery) return null
        val charges = HashMap<Int, Charge>()
        val count = p[1].u()
        for (k in 0 until count) {
            val o = 2 + 2 * k
            if (o + 1 >= p.size) break
            val level = p[o + 1].u()
            charges[p[o].u()] = Charge(level and 0x7F, level and 0x80 != 0)
        }
        if (charges.isEmpty()) return null
        return Battery(left = charges[1], right = charges[2], case = charges[3])
    }

    /** True if a set-mode acknowledgement reports success. */
    fun isSuccessAck(frame: Frame) =
        frame.cmd == responseTo(CMD_SET_NOISE_MODE) && frame.payload.firstOrNull()?.u() == 0

    private fun Byte.u() = toInt() and 0xFF

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
