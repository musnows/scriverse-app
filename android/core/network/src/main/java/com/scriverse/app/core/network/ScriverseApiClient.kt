package com.scriverse.app.core.network

import com.scriverse.app.core.model.NativeClientProtocol
import com.scriverse.app.core.model.WorkspaceCompatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ApiException(val status: Int, message: String) : Exception(message)

data class HealthResult(
    val compatibility: WorkspaceCompatibility,
    val protocol: NativeClientProtocol?,
)

class ScriverseApiClient(
    rawOrigin: String,
    private val tokenProvider: () -> String?,
) {
    private val origin = OriginPolicy.normalize(rawOrigin, allowLoopbackHttp = true)

    suspend fun health(clientVersion: String): HealthResult = withContext(Dispatchers.IO) {
        val response = request("GET", "/api/health?client=android&version=$clientVersion")
        val root = JSONObject(response)
        val data = root.optJSONObject("data") ?: root
        val native = data.optJSONObject("nativeClientProtocol")
        if (native == null) {
            return@withContext HealthResult(WorkspaceCompatibility.ONLINE_ONLY, null)
        }
        val protocol = NativeClientProtocol(
            minimumAppVersion = native.optString("minimumAppVersion", "0.0.0"),
            shellProtocol = native.optInt("shellProtocol", 0),
            syncProtocol = native.optInt("syncProtocol", 0),
            serverVersion = data.optString("version", "unknown"),
        )
        HealthResult(
            compatibility = if (protocol.shellProtocol >= 1) {
                WorkspaceCompatibility.COMPATIBLE
            } else {
                WorkspaceCompatibility.INCOMPATIBLE
            },
            protocol = protocol,
        )
    }

    suspend fun login(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(request("POST", "/api/app/auth/login", payload.toString()))
    }

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(request("GET", path))
    }

    suspend fun post(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(request("POST", path, payload.toString()))
    }

    suspend fun patch(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(request("PATCH", path, payload.toString()))
    }

    suspend fun stream(
        path: String,
        payload: JSONObject,
        onEvent: suspend (event: String, data: String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = open(path)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setRequestProperty("Content-Type", "application/json")
        payload.toString().byteInputStream().use { input ->
            connection.outputStream.use { output -> input.copyTo(output) }
        }
        ensureSuccess(connection)
        BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            var event = "message"
            val data = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                    line.isEmpty() && data.isNotEmpty() -> {
                        onEvent(event, data.toString())
                        event = "message"
                        data.clear()
                    }
                }
            }
        }
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val connection = open(path)
        connection.requestMethod = method
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        }
        ensureSuccess(connection)
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = reader.readText()
            require(result.length <= MAX_RESPONSE_CHARS) { "Response is too large" }
            result
        }
    }

    private fun open(path: String): HttpURLConnection {
        require(path.startsWith('/')) { "API path must be absolute" }
        val target = URI(origin).resolve(path)
        require(OriginPolicy.sameOrigin(origin, "${target.scheme}://${target.rawAuthority}")) {
            "Cross-origin request is forbidden"
        }
        return (URL(target.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (this is HttpsURLConnection) {
                hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            }
        }
    }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status in 300..399) throw ApiException(status, "Redirects are forbidden")
        if (status !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText().take(1024) }
            throw ApiException(status, message ?: "Request failed")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RESPONSE_CHARS = 4_000_000
    }
}
