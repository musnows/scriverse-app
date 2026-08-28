package com.scriverse.app.core.database

import kotlin.math.min
import kotlin.random.Random

object SyncBackoff {
    fun delayMillis(attempt: Int, retryAfterSeconds: Long? = null, jitter: Double = Random.nextDouble()): Long {
        retryAfterSeconds?.let { return min(it.coerceAtLeast(0) * 1_000, MAX_DELAY_MS) }
        val exponent = attempt.coerceIn(0, 10)
        val base = min(INITIAL_DELAY_MS shl exponent, MAX_DELAY_MS)
        return (base * (0.75 + jitter.coerceIn(0.0, 1.0) * 0.5)).toLong()
    }

    private const val INITIAL_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 30 * 60 * 1_000L
}
