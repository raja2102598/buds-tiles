package io.github.raja2102598.budstiles.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import io.github.raja2102598.budstiles.R
import io.github.raja2102598.budstiles.bluetooth.Buds
import io.github.raja2102598.budstiles.data.Settings
import io.github.raja2102598.budstiles.protocol.NoiseMode

/**
 * Invisible entry point for app shortcuts and automation (Samsung Modes and
 * Routines, Tasker, …). Starts the mode change in the background and closes
 * immediately; only a failure shows a toast.
 */
class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = MODES[intent?.action]
        if (mode != null) apply(mode)
        finish()
    }

    private fun apply(mode: NoiseMode) {
        val app = applicationContext
        if (!Buds.hasPermission(app) || Settings(app).earbuds == null) {
            toast(R.string.shortcut_setup_needed)
            return
        }
        Buds.updateConnectionState(app)
        if (Settings(app).connected == false) {
            toast(R.string.tile_not_connected)
            return
        }
        Buds.setNoiseMode(app, mode) { result ->
            if (result.isFailure) toast(R.string.shortcut_failed)
        }
    }

    private fun toast(message: Int) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val PREFIX = "io.github.raja2102598.budstiles.action."
        const val ACTION_NOISE_CANCELLING = PREFIX + "NOISE_CANCELLING"
        const val ACTION_TRANSPARENCY = PREFIX + "TRANSPARENCY"
        const val ACTION_OFF = PREFIX + "OFF"

        private val MODES = mapOf(
            ACTION_NOISE_CANCELLING to NoiseMode.NOISE_CANCELLING,
            ACTION_TRANSPARENCY to NoiseMode.TRANSPARENCY,
            ACTION_OFF to NoiseMode.OFF,
        )
    }
}
