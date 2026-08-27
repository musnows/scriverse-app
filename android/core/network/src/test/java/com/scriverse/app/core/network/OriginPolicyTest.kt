package com.scriverse.app.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Assert.assertThrows

class OriginPolicyTest {
    @Test
    fun normalizesHttpsOrigin() {
        assertThat(OriginPolicy.normalize(" HTTPS://例子.测试:443/ "))
            .isEqualTo("https://xn--fsqu00a.xn--0zwm56d")
    }

    @Test
    fun rejectsCredentialsAndPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            OriginPolicy.normalize("https://user:pass@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OriginPolicy.normalize("https://example.com/api")
        }
    }

    @Test
    fun onlyAllowsHttpForLoopbackWhenExplicit() {
        assertThat(OriginPolicy.normalize("http://127.0.0.1:43111", true))
            .isEqualTo("http://127.0.0.1:43111")
        assertThrows(IllegalArgumentException::class.java) {
            OriginPolicy.normalize("http://example.com", true)
        }
    }
}
