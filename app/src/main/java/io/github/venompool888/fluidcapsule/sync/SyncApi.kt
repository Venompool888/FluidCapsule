package io.github.venompool888.fluidcapsule.sync

import android.content.Context
import android.security.NetworkSecurityPolicy
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class SyncApi(private val server: String, private val token: String, private val versionName: String) {
    fun get(path: String): JSONObject = request("GET", path)
    fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)
    fun put(path: String, body: JSONObject): JSONObject = request("PUT", path, body)
    // Leave room for the server's idle response before the client read timeout.
    fun waitForMessages(onOpen: (HttpURLConnection) -> Unit): Boolean =
        request("GET", "/api/v1/messages/wait?timeout=10", onOpen = onOpen).getBoolean("available")

    private fun request(
        method: String, path: String, body: JSONObject? = null, onOpen: ((HttpURLConnection) -> Unit)? = null,
    ): JSONObject {
        val connection = URI(server + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FluidCapsule-Android/$versionName")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            onOpen?.invoke(connection)
            if (body != null) writeBody(connection, body)
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "服务器返回 $status"
                throw ApiException(status, message)
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }

    private fun writeBody(connection: HttpURLConnection, body: JSONObject) {
        val encoded = body.toString().toByteArray(Charsets.UTF_8)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setFixedLengthStreamingMode(encoded.size)
        connection.outputStream.use { it.write(encoded) }
    }

    class ApiException(val status: Int, message: String) : Exception(message)

    companion object {
        /**
         * Validates a server address. Plain HTTP is accepted only where the network security
         * config permits cleartext (debug builds allow loopback for the dev server); anywhere
         * else the request would fail at connect time, so it is rejected here with a clear reason.
         */
        fun normalizeServer(
            raw: String,
            cleartextPermitted: (String) -> Boolean = { NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(it) },
        ): String {
            val server = raw.trim().trimEnd('/')
            val uri = try {
                URI.create(server).also {
                    require(it.scheme.equals("https", true) || it.scheme.equals("http", true))
                    require(it.host != null && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null)
                }
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("服务器地址必须是完整的 HTTP(S) 地址", error)
            }
            require(!uri.scheme.equals("http", true) || cleartextPermitted(uri.host)) {
                "这个地址使用未加密的 HTTP。为保护注册码和密码，请使用 HTTPS 地址。"
            }
            return server
        }

        fun forConfig(context: Context, config: SyncConfig): SyncApi =
            SyncApi(config.server, config.token(), context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())

        fun forConnect(context: Context, server: String): SyncApi =
            SyncApi(server, "", context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
    }
}
