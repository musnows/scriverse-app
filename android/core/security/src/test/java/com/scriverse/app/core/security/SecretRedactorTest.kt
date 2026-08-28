package com.scriverse.app.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecretRedactorTest {
    @Test
    fun redactsBearerAndStructuredSecrets() {
        val input = "Authorization: Bearer abc.def token=raw password=hello safe=value"
        val output = SecretRedactor.redact(input)

        assertThat(output).doesNotContain("abc.def")
        assertThat(output).doesNotContain("raw")
        assertThat(output).doesNotContain("hello")
        assertThat(output).contains("safe=value")
    }
}
