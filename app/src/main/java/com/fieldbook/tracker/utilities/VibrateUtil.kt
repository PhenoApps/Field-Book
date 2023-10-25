package com.fieldbook.tracker.utilities

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VibrateUtil @Inject constructor(@ApplicationContext context: Context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator

    fun vibrate(duration: Long) {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(1L, 5L, 8L, 5L, 1L), -1))
            } else {
                vibrator.vibrate(duration)
            }
        }
    }
}