package io.github.raja2102598.budstiles.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.raja2102598.budstiles.data.Earbuds
import io.github.raja2102598.budstiles.data.Settings
import io.github.raja2102598.budstiles.protocol.Battery
import io.github.raja2102598.budstiles.protocol.NoiseMode
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Async entry point used by the UI and tiles. Each call opens a short session,
 * does its work and disconnects. Calls run one at a time on a single background
 * thread, so rapid taps queue instead of fighting over the socket. Results are
 * delivered on the main thread and also saved to [Settings].
 */
object Buds {

    private const val TAG = "Buds"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Status(val mode: NoiseMode?, val battery: Battery?)

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Paired devices, earbud-looking names first. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<Earbuds> {
        if (!hasPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        return adapter.bondedDevices.orEmpty()
            .map { Earbuds(it.address, it.name ?: it.address) }
            .sortedWith(compareByDescending<Earbuds> { looksLikeEarbuds(it.name) }.thenBy { it.name })
    }

    private fun looksLikeEarbuds(name: String): Boolean {
        val n = name.lowercase()
        return listOf("buds", "oneplus", "oppo", "realme", "enco").any { it in n }
    }

    /** Switches the noise mode. [done] receives the new mode, or an error. */
    fun setNoiseMode(context: Context, mode: NoiseMode, done: (Result<NoiseMode>) -> Unit) {
        val settings = Settings(context)
        run(context, done) { session ->
            session.setNoiseMode(mode, settings.ancLevel).also { settings.noiseMode = it }
        }
    }

    /** Reads the current mode and battery. */
    fun refresh(context: Context, done: (Result<Status>) -> Unit) {
        val settings = Settings(context)
        run(context, done) { session ->
            val mode = session.queryNoiseMode()
            val battery = session.queryBattery()
            if (mode != null) settings.noiseMode = mode
            Status(mode, battery)
        }
    }

    /** A failure with a message fit to show the user. */
    class BudsException(message: String, cause: Throwable) : Exception(message, cause)

    private fun userMessage(e: Throwable): String = when (e) {
        is SecurityException -> "Bluetooth permission missing"
        is IOException, is IllegalStateException -> e.message ?: "Connection failed"
        else -> "Unexpected error"
    }

    private fun <T> run(context: Context, done: (Result<T>) -> Unit, block: (BudsSession) -> T) {
        val app = context.applicationContext
        worker.execute {
            val result = runCatching {
                check(hasPermission(app)) { "Bluetooth permission not granted" }
                val earbuds = checkNotNull(Settings(app).earbuds) { "No earbuds selected" }
                BudsSession.open(app, earbuds).use(block)
            }.recoverCatching { e ->
                Log.w(TAG, "Operation failed", e)
                throw BudsException(userMessage(e), e)
            }
            main.post { done(result) }
        }
    }
}
