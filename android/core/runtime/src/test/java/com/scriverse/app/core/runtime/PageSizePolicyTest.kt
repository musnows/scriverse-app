package com.scriverse.app.core.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageSizePolicyTest {
    @Test
    fun acceptsCurrentAndroidPageSizes() {
        assertThat(PageSizePolicy.isSupported(4_096)).isTrue()
        assertThat(PageSizePolicy.isSupported(16_384)).isTrue()
        assertThat(PageSizePolicy.isSupported(12_000)).isFalse()
    }
}
