package io.github.raja2102598.budstiles.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import io.github.raja2102598.budstiles.data.Earbuds
import io.github.raja2102598.budstiles.protocol.AncLevel
import io.github.raja2102598.budstiles.protocol.Battery
import io.github.raja2102598.budstiles.protocol.NoiseMode
import io.github.raja2102598.budstiles.protocol.OpoProtocol
import io.github.raja2102598.budstiles.protocol.OpoProtocol.Frame
import java.io.Closeable
import java.io.IOException

/**
 * A blocking control session with one pair of earbuds. Open it off the main
 * thread, use it, close it. Callers must hold BLUETOOTH_CONNECT.
 */
@SuppressLint("MissingPermission")
class BudsSession private constructor(private val socket: BluetoothSocket) : Closeable {

    private val input = socket.inputStream
    private val output = socket.outputStream
    private val parser = OpoProtocol.FrameParser()
    private val readBuffer = ByteArray(1024)

    /** Latest noise mode the buds reported during this session. */
    var reportedMode: NoiseMode? = null
        private set

    /** Latest battery levels the buds reported during this session. */
    var reportedBattery: Battery? = null
        private set

    /** Sets the noise mode and returns what the buds report afterwards. */
    fun setNoiseMode(mode: NoiseMode, level: AncLevel): NoiseMode {
        val ack = request(OpoProtocol.setNoiseMode(mode, level), OpoProtocol.CMD_SET_NOISE_MODE)
        if (!OpoProtocol.isSuccessAck(ack)) throw IOException("Earbuds rejected the mode change")
        drain(EVENT_GRACE_MS) // the matching event usually follows the ack
        return reportedMode ?: mode
    }

    fun queryNoiseMode(): NoiseMode? {
        request(OpoProtocol.queryNoiseMode(), OpoProtocol.CMD_QUERY_NOISE_MODE)
        return reportedMode
    }

    fun queryBattery(): Battery? {
        request(OpoProtocol.queryBattery(), OpoProtocol.CMD_QUERY_BATTERY)
        return reportedBattery
    }

    override fun close() {
        try { socket.close() } catch (_: IOException) {}
    }

    /** Sends [packet] and waits for the reply to [cmd]. */
    private fun request(packet: ByteArray, cmd: Int, timeoutMs: Long = REPLY_TIMEOUT_MS): Frame {
        output.write(packet)
        output.flush()
        return awaitFrame(OpoProtocol.responseTo(cmd), timeoutMs)
            ?: throw IOException("No reply to command 0x%04X".format(cmd))
    }

    private fun drain(timeoutMs: Long) {
        awaitFrame(cmd = -1, timeoutMs = timeoutMs)
    }

    /**
     * Reads frames until one with [cmd] arrives or [timeoutMs] passes.
     * Every frame seen on the way updates [reportedMode] / [reportedBattery].
     */
    private fun awaitFrame(cmd: Int, timeoutMs: Long): Frame? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() == 0) {
                Thread.sleep(POLL_MS)
                continue
            }
            val n = input.read(readBuffer)
            if (n < 0) throw IOException("Connection closed by earbuds")
            var match: Frame? = null
            for (frame in parser.feed(readBuffer, n)) {
                OpoProtocol.parseNoiseMode(frame)?.let { reportedMode = it }
                OpoProtocol.parseBattery(frame)?.let { reportedBattery = it }
                if (match == null && frame.cmd == cmd) match = frame
            }
            if (match != null) return match
        }
        return null
    }

    companion object {
        private const val TAG = "BudsSession"
        private const val REPLY_TIMEOUT_MS = 2500L
        private const val EVENT_GRACE_MS = 250L
        private const val POLL_MS = 15L

        /**
         * Connects to [earbuds] and initialises the session. The buds ignore
         * commands until they have seen the handshake and event registration.
         */
        fun open(context: Context, earbuds: Earbuds): BudsSession {
            val session = BudsSession(connect(context, earbuds))
            try {
                session.request(OpoProtocol.handshake(), OpoProtocol.CMD_HANDSHAKE)
                session.request(OpoProtocol.registerEvents(), OpoProtocol.CMD_REGISTER_EVENTS)
            } catch (e: Exception) {
                session.close()
                throw e
            }
            return session
        }

        private fun connect(context: Context, earbuds: Earbuds): BluetoothSocket {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IOException("Bluetooth is not available")
            if (!adapter.isEnabled) throw IOException("Bluetooth is off")
            val device = adapter.getRemoteDevice(earbuds.address)
            adapter.cancelDiscovery()

            for (uuid in OpoProtocol.SERVICE_UUIDS) {
                try {
                    return device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
                } catch (e: IOException) {
                    Log.d(TAG, "RFCOMM $uuid failed: ${e.message}")
                }
            }
            try {
                // Hidden API: connect to a raw RFCOMM channel when SDP lookup fails.
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                return (method.invoke(device, OpoProtocol.FALLBACK_CHANNEL) as BluetoothSocket)
                    .apply { connect() }
            } catch (e: Exception) {
                throw IOException("Could not connect to ${earbuds.name}", e)
            }
        }
    }
}
