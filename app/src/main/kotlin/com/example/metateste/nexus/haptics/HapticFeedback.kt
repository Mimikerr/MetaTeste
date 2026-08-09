package com.example.metateste.nexus.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Distinct vibration patterns so the user can tell a command succeeded from a failure without looking. */
class HapticFeedback(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun success() = pulse(SUCCESS_PATTERN)

    fun failure() = pulse(FAILURE_PATTERN)

    private fun pulse(pattern: LongArray) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    companion object {
        private val SUCCESS_PATTERN = longArrayOf(0, 40)
        private val FAILURE_PATTERN = longArrayOf(0, 60, 80, 60)
    }
}
