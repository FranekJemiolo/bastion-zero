package com.bastionzero.hal

import com.bastionzero.haptics.HapticChord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PanicEvent(
    val triggerTimestampMs: Long,
    val clickCount: Int,
    val recommendedHapticChord: HapticChord,
    val emergencyCode: String,
)

/**
 * Physical Hardware Key Panic Trigger Engine.
 * Monitors hardware button click timing (e.g. Volume-Down / PTT rapid presses).
 * Fires silent SOS alarms without turning on the display or unlocking the phone.
 */
class HardwarePanicTrigger(
    private val requiredClickCount: Int = 5,
    private val clickWindowMs: Long = 2500L,
) {

    private val clickTimestamps = ArrayList<Long>()

    private val _isArmed = MutableStateFlow(true)
    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

    private val _panicEvents = MutableSharedFlow<PanicEvent>(extraBufferCapacity = 8)
    val panicEvents: SharedFlow<PanicEvent> = _panicEvents.asSharedFlow()

    fun setArmed(armed: Boolean) {
        _isArmed.value = armed
        if (!armed) clickTimestamps.clear()
    }

    /**
     * Record a hardware button press event.
     * Returns true if the panic sequence was triggered.
     */
    fun registerButtonPress(timestampMs: Long): Boolean {
        if (!_isArmed.value) return false

        // Purge expired clicks outside window
        clickTimestamps.removeAll { timestampMs - it > clickWindowMs }
        clickTimestamps.add(timestampMs)

        if (clickTimestamps.size >= requiredClickCount) {
            clickTimestamps.clear()
            val event = PanicEvent(
                triggerTimestampMs = timestampMs,
                clickCount = requiredClickCount,
                recommendedHapticChord = HapticChord.MEDICAL_SOS,
                emergencyCode = "SOS_HARDWARE_KEY_TRIGGER"
            )
            _panicEvents.tryEmit(event)
            return true
        }

        return false
    }

    fun reset() {
        clickTimestamps.clear()
    }
}
