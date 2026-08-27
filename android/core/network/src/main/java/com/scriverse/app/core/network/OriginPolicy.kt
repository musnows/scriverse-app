package com.scriverse.app.core.network

import java.net.IDN
import java.net.URI

object OriginPolicy {
    fun normalize(raw: String, allowLoopbackHttp: Boolean = false): String {
        require(raw.length <= 2048) { "Server URL is too long" }
        val uri = URI(raw.trim())
        val authority = uri.rawAuthority ?: error("URL host is required")
        require(uri.userInfo == null && '@' !in authority) { "Credentials in URL are forbidden" }
        require(uri.query == null && uri.fragment == null) { "Query and fragment are forbidden" }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "Server URL must be an origin" }

        val scheme = uri.scheme?.lowercase() ?: error("URL scheme is required")
        val (rawHost, parsedPort) = parseAuthority(authority)
        val host = if (rawHost.contains(':')) rawHost.lowercase() else IDN.toASCII(rawHost).lowercase()
        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        require(scheme == "https" || (allowLoopbackHttp && scheme == "http" && isLoopback)) {
            "Remote servers must use HTTPS"
        }

        val defaultPort = (scheme == "https" && parsedPort == 443) || (scheme == "http" && parsedPort == 80)
        val port = if (parsedPort == null || defaultPort) "" else ":$parsedPort"
        val normalizedHost = if (host.contains(':')) "[$host]" else host
        return "$scheme://$normalizedHost$port"
    }

    private fun parseAuthority(authority: String): Pair<String, Int?> {
        if (authority.startsWith('[')) {
            val end = authority.indexOf(']')
            require(end > 1) { "Invalid IPv6 host" }
            val host = authority.substring(1, end)
            val suffix = authority.substring(end + 1)
            val port = when {
                suffix.isEmpty() -> null
                suffix.startsWith(':') -> suffix.drop(1).toIntOrNull()
                else -> null
            }
            require(suffix.isEmpty() || port != null) { "Invalid port" }
            return host to port
        }
        val match = Regex(":([0-9]{1,5})$").find(authority)
        val port = match?.groupValues?.get(1)?.toInt()
        require(port == null || port in 1..65_535) { "Invalid port" }
        return (match?.let { authority.substring(0, it.range.first) } ?: authority) to port
    }

    fun sameOrigin(expected: String, actual: String): Boolean =
        runCatching { normalize(expected, true) == normalize(actual, true) }.getOrDefault(false)
}
