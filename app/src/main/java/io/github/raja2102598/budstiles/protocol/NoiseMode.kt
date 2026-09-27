package io.github.raja2102598.budstiles.protocol

/** The three noise-control states the app exposes. */
enum class NoiseMode {
    OFF,
    TRANSPARENCY,
    NOISE_CANCELLING,
}

/**
 * Noise-cancelling strengths. [mask] is the bit field sent after `01 01` in the
 * set-mode command. Which levels exist depends on the model; [DEEP] (the
 * strongest) works on every model we know of, so it is the default.
 */
enum class AncLevel(val mask: ByteArray) {
    DEEP(byteArrayOf(0x10)),
    MEDIUM(byteArrayOf(0x20)),
    LIGHT(byteArrayOf(0x40)),
    SMART(byteArrayOf(0x80.toByte())),
    ADAPTIVE(byteArrayOf(0x00, 0x08)),
}

/** Charge level of one component; [percent] is 0–100. */
data class Charge(val percent: Int, val charging: Boolean)

data class Battery(val left: Charge?, val right: Charge?, val case: Charge?)
