package uk.elizabeth.aac.core.privacy

import kotlinx.serialization.Serializable

/**
 * Slows down guessing of the carer PIN. After [FREE_ATTEMPTS] wrong PINs, each further wrong
 * PIN locks entry for twice as long as the last (30 s, 1 min, 2 min ... up to 15 min).
 * Stored with the app's data, so restarting the app does not reset it.
 */
@Serializable
data class PinGuard(val failures: Int = 0, val lockedUntilMillis: Long = 0) {

    fun isLocked(nowMillis: Long) = nowMillis < lockedUntilMillis

    fun secondsLeft(nowMillis: Long): Long = ((lockedUntilMillis - nowMillis).coerceAtLeast(0) + 999) / 1000

    fun afterFailure(nowMillis: Long): PinGuard {
        val count = failures + 1
        if (count < FREE_ATTEMPTS) return copy(failures = count)
        val lockMs = (BASE_LOCK_MS shl (count - FREE_ATTEMPTS).coerceAtMost(5)).coerceAtMost(MAX_LOCK_MS)
        return PinGuard(count, nowMillis + lockMs)
    }

    fun afterSuccess() = PinGuard()

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 15 * 60_000L
    }
}
