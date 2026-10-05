package com.bastionzero.haptics

/**
 * Vibration pattern in Android `VibrationEffect.createWaveform` layout: index 0 is an
 * initial delay (off); even indices are OFF durations, odd indices are ON durations.
 * [amplitudes] is parallel (0 = off, 1..255 = strength).
 */
class HapticPattern(val timingsMs: LongArray, val amplitudes: IntArray) {
    init {
        require(timingsMs.size == amplitudes.size) { "timings and amplitudes must be parallel" }
    }
}

/** Eyes-free vocabulary for the most critical mesh events. */
enum class HapticChord(val pattern: HapticPattern) {
    /** Fast, sharp, escalating taps (rattlesnake): hazard approaching. */
    HAZARD_APPROACHING(rattlesnake()),

    /** One long, low, rolling rumble (purr): safe zone / all clear. */
    ALL_CLEAR(HapticPattern(
        longArrayOf(0, 300, 50, 400, 50, 500, 50, 400, 50, 300),
        intArrayOf(0, 60, 0, 90, 0, 120, 0, 90, 0, 60),
    )),

    /** Two heavy thuds like a heartbeat: medical SOS received. */
    MEDICAL_SOS(HapticPattern(
        longArrayOf(0, 110, 90, 150),
        intArrayOf(0, 255, 0, 255),
    )),
}

interface HapticPlayer {
    fun play(chord: HapticChord)
}

/** Used where no haptic driver exists yet (iOS until Core Haptics lands). */
class NoOpHapticPlayer : HapticPlayer {
    override fun play(chord: HapticChord) = Unit
}

private fun rattlesnake(): HapticPattern {
    val gaps = longArrayOf(140, 120, 100, 85, 70, 58, 48, 40, 34, 28) // shrinking = escalating
    val pulses = gaps.size + 1
    val onMs = 30L
    val timings = ArrayList<Long>().apply { add(0) }
    val amps = ArrayList<Int>().apply { add(0) }
    for (i in 0 until pulses) {
        timings.add(onMs)
        amps.add(70 + (185 * i) / (pulses - 1)) // 70 .. 255
        if (i < gaps.size) {
            timings.add(gaps[i])
            amps.add(0)
        }
    }
    return HapticPattern(timings.toLongArray(), amps.toIntArray())
}
