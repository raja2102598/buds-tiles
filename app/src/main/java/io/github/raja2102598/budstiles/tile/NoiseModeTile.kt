package io.github.raja2102598.budstiles.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.raja2102598.budstiles.R
import io.github.raja2102598.budstiles.bluetooth.Buds
import io.github.raja2102598.budstiles.data.Settings
import io.github.raja2102598.budstiles.protocol.NoiseMode
import io.github.raja2102598.budstiles.ui.MainActivity

/**
 * A Quick Settings tile that toggles one noise mode: tap to switch to [mode],
 * tap again to switch the earbuds back to off.
 *
 * The subtitle carries the state, so it shows on the large tile size.
 */
abstract class NoiseModeTile(
    private val mode: NoiseMode,
    private val labelRes: Int,
    private val iconRes: Int,
) : TileService() {

    private val settings by lazy { Settings(this) }
    private var switching = false
    private var settingsObserver: AutoCloseable? = null

    override fun onStartListening() {
        super.onStartListening()
        // Both tiles are visible at once, so each redraws itself when the other
        // (or the app) changes the mode.
        settingsObserver = settings.observe { if (!switching) render() }
        render()
    }

    override fun onStopListening() {
        settingsObserver?.close()
        settingsObserver = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (switching) return
        if (!Buds.hasPermission(this) || settings.earbuds == null) {
            openApp()
            return
        }
        val target = if (settings.noiseMode == mode) NoiseMode.OFF else mode
        switching = true
        render(getString(R.string.tile_switching))

        Buds.setNoiseMode(this, target) { result ->
            switching = false
            render(if (result.isFailure) getString(R.string.tile_failed) else null)
        }
    }

    private fun render(subtitleOverride: String? = null) {
        val tile = qsTile ?: return
        val active = settings.noiseMode == mode
        tile.label = getString(labelRes)
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.state = when {
            settings.earbuds == null -> Tile.STATE_UNAVAILABLE
            active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleOverride ?: getString(if (active) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

class NoiseCancellingTile :
    NoiseModeTile(NoiseMode.NOISE_CANCELLING, R.string.noise_cancelling, R.drawable.ic_noise_cancelling)

class TransparencyTile :
    NoiseModeTile(NoiseMode.TRANSPARENCY, R.string.transparency, R.drawable.ic_transparency)
