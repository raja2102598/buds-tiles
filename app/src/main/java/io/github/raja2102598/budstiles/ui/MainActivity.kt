package io.github.raja2102598.budstiles.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.raja2102598.budstiles.R
import io.github.raja2102598.budstiles.bluetooth.Buds
import io.github.raja2102598.budstiles.data.Settings
import io.github.raja2102598.budstiles.databinding.ActivityMainBinding
import io.github.raja2102598.budstiles.protocol.AncLevel
import io.github.raja2102598.budstiles.protocol.Battery
import io.github.raja2102598.budstiles.protocol.NoiseMode
import io.github.raja2102598.budstiles.tile.NoiseCancellingTile
import io.github.raja2102598.budstiles.tile.TransparencyTile

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { Settings(this) }
    private var battery: Battery? = null

    /** Suppresses the toggle-group listener while we update it from code. */
    private var updatingModeGroup = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onPermissionReady() else showPermissionNeeded()
        }

    private val modeButtons by lazy {
        mapOf(
            NoiseMode.OFF to R.id.modeOff,
            NoiseMode.TRANSPARENCY to R.id.modeTransparency,
            NoiseMode.NOISE_CANCELLING to R.id.modeNoiseCancelling,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chooseButton.setOnClickListener { chooseEarbuds() }
        binding.refreshButton.setOnClickListener { refresh() }
        binding.modeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !updatingModeGroup) {
                modeButtons.entries.first { it.value == id }.key.let(::setMode)
            }
        }
        setUpAncLevel()
        setUpAddTileButtons()
        render()

        if (Buds.hasPermission(this)) {
            onPermissionReady()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun onPermissionReady() {
        if (settings.earbuds == null) chooseEarbuds() else refresh()
    }

    private fun render(statusOverride: String? = null) {
        val earbuds = settings.earbuds
        binding.earbudsName.text = earbuds?.name ?: getString(R.string.no_earbuds)
        binding.refreshButton.isEnabled = earbuds != null
        binding.modeGroup.isEnabled = earbuds != null
        binding.status.text = statusOverride ?: statusText()

        updatingModeGroup = true
        val mode = settings.noiseMode
        if (mode == null) binding.modeGroup.clearChecked()
        else binding.modeGroup.check(modeButtons.getValue(mode))
        updatingModeGroup = false
    }

    private fun statusText(): String {
        val mode = settings.noiseMode ?: return getString(R.string.status_unknown)
        val modeText = getString(mode.labelRes())
        val batteryText = battery?.let(::batteryText)
        return if (batteryText.isNullOrEmpty()) getString(R.string.status_mode, modeText)
        else getString(R.string.status_mode_battery, modeText, batteryText)
    }

    private fun batteryText(b: Battery) = listOfNotNull(
        b.left?.let { getString(R.string.battery_left, it.percent) },
        b.right?.let { getString(R.string.battery_right, it.percent) },
        b.case?.let { getString(R.string.battery_case, it.percent) },
    ).joinToString("  ")

    private fun refresh() {
        if (settings.earbuds == null) return
        render(getString(R.string.status_connecting))
        Buds.refresh(this) { result ->
            result.onSuccess { battery = it.battery }
            render(result.exceptionOrNull()?.let { getString(R.string.status_error, it.message) })
        }
    }

    private fun setMode(mode: NoiseMode) {
        render(getString(R.string.status_connecting))
        Buds.setNoiseMode(this, mode) { result ->
            render(result.exceptionOrNull()?.let { getString(R.string.status_error, it.message) })
        }
    }

    private fun chooseEarbuds() {
        if (!Buds.hasPermission(this)) return showPermissionNeeded()
        val devices = Buds.pairedDevices(this)
        if (devices.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_paired_devices, Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_earbuds_title)
            .setItems(devices.map { it.name }.toTypedArray()) { _, which ->
                settings.earbuds = devices[which]
                settings.noiseMode = null
                battery = null
                refresh()
            }
            .show()
    }

    private fun setUpAncLevel() {
        val levels = AncLevel.entries
        val labels = levels.map { getString(it.labelRes()) }
        binding.ancLevel.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        binding.ancLevel.setText(getString(settings.ancLevel.labelRes()), false)
        binding.ancLevel.setOnItemClickListener { _, _, position, _ ->
            settings.ancLevel = levels[position]
            if (settings.noiseMode == NoiseMode.NOISE_CANCELLING) setMode(NoiseMode.NOISE_CANCELLING)
        }
    }

    private fun setUpAddTileButtons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            binding.addTileButtons.visibility = View.GONE
            return
        }
        binding.addNoiseCancellingTile.setOnClickListener {
            requestAddTile(NoiseCancellingTile::class.java, R.string.noise_cancelling, R.drawable.ic_noise_cancelling)
        }
        binding.addTransparencyTile.setOnClickListener {
            requestAddTile(TransparencyTile::class.java, R.string.transparency, R.drawable.ic_transparency)
        }
    }

    private fun requestAddTile(tile: Class<*>, labelRes: Int, iconRes: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, tile),
            getString(labelRes),
            Icon.createWithResource(this, iconRes),
            mainExecutor,
        ) { }
    }

    private fun showPermissionNeeded() {
        Snackbar.make(binding.root, R.string.permission_needed, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.grant) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            .show()
    }
}

private fun NoiseMode.labelRes() = when (this) {
    NoiseMode.OFF -> R.string.off
    NoiseMode.TRANSPARENCY -> R.string.transparency
    NoiseMode.NOISE_CANCELLING -> R.string.noise_cancelling
}

private fun AncLevel.labelRes() = when (this) {
    AncLevel.DEEP -> R.string.anc_deep
    AncLevel.MEDIUM -> R.string.anc_medium
    AncLevel.LIGHT -> R.string.anc_light
    AncLevel.SMART -> R.string.anc_smart
    AncLevel.ADAPTIVE -> R.string.anc_adaptive
}
