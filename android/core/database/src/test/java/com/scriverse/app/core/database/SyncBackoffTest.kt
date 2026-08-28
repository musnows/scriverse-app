package com.scriverse.app.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncBackoffTest {
    @Test
    fun growsExponentiallyAndHonorsRetryAfter() {
        assertThat(SyncBackoff.delayMillis(0, jitter = 0.5)).isEqualTo(2_000)
        assertThat(SyncBackoff.delayMillis(3, jitter = 0.5)).isEqualTo(16_000)
        assertThat(SyncBackoff.delayMillis(3, retryAfterSeconds = 42)).isEqualTo(42_000)
    }
}
