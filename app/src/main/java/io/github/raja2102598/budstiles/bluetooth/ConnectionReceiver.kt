package io.github.raja2102598.budstiles.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.raja2102598.budstiles.data.Settings

/**
 * Tracks whether the chosen earbuds are connected, so the tiles can show
 * "Not connected" instead of a stale mode. On reconnect it also reads the
 * current mode, since it may have changed while we weren't listening.
 */
class ConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) settings.connected = false
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (isOurEarbuds(intent, settings)) settings.connected = false
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (!isOurEarbuds(intent, settings)) return
                settings.connected = true
                // The control channel isn't ready the instant the link comes up.
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    Buds.refresh(context) { pending.finish() }
                }, CONTROL_CHANNEL_DELAY_MS)
            }
        }
    }

    private fun isOurEarbuds(intent: Intent, settings: Settings): Boolean {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return device != null && device.address == settings.earbuds?.address
    }

    private companion object {
        const val CONTROL_CHANNEL_DELAY_MS = 3000L
    }
}
