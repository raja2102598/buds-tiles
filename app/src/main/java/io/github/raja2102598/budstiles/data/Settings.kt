package io.github.raja2102598.budstiles.data

import android.content.Context
import io.github.raja2102598.budstiles.protocol.AncLevel
import io.github.raja2102598.budstiles.protocol.NoiseMode

/** A paired pair of earbuds, identified by MAC address. */
data class Earbuds(val address: String, val name: String)

/** Persistent app settings, backed by SharedPreferences. */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    var earbuds: Earbuds?
        get() {
            val address = prefs.getString(KEY_ADDRESS, null) ?: return null
            return Earbuds(address, prefs.getString(KEY_NAME, null) ?: address)
        }
        set(value) {
            prefs.edit()
                .putString(KEY_ADDRESS, value?.address)
                .putString(KEY_NAME, value?.name)
                .apply()
        }

    /** Last mode the buds reported or accepted. Only as fresh as the last contact. */
    var noiseMode: NoiseMode?
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { NoiseMode.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_MODE, value?.name).apply()

    var ancLevel: AncLevel
        get() = prefs.getString(KEY_ANC_LEVEL, null)
            ?.let { runCatching { AncLevel.valueOf(it) }.getOrNull() }
            ?: AncLevel.DEEP
        set(value) = prefs.edit().putString(KEY_ANC_LEVEL, value.name).apply()

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val KEY_MODE = "mode"
        const val KEY_ANC_LEVEL = "anc_level"
    }
}
