package com.bastionzero.android

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer

/**
 * Plays chords through the device's vibrator using amplitude-modulated waveforms
 * (PWM-style: on/off timing plus per-step strength). Falls back to timing-only
 * on motors without amplitude control.
 */
class AndroidHapticPlayer(context: Context) : HapticPlayer {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    override fun play(chord: HapticChord) {
        if (!vibrator.hasVibrator()) return
        val p = chord.pattern
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(p.timingsMs, p.amplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(p.timingsMs, NO_REPEAT)
        }
        vibrator.vibrate(effect)
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}
