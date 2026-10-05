package com.bastionzero.mesh

/**
 * Lamport logical clock. Orders mesh events without trusting wall-clock time,
 * which drifts freely once devices lose NTP.
 *
 * Not thread-safe: confine to the single dispatcher that owns mesh state.
 */
class LamportClock(initial: Long = 0L) {
    var value: Long = initial
        private set

    /** Advance for a local event (e.g. creating a packet) and return the new value. */
    fun tick(): Long {
        value += 1
        return value
    }

    /** Merge a remote timestamp on receive: `max(local, remote) + 1`. */
    fun observe(remote: Long): Long {
        value = maxOf(value, remote) + 1
        return value
    }
}
