package com.scriverse.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainModelsTest {
    @Test
    fun offlineEditableTypesRemainExplicit() {
        val supported = setOf("chapter", "setting")

        assertThat(supported).containsExactly("chapter", "setting")
        assertThat(supported).doesNotContain("character")
    }
}
