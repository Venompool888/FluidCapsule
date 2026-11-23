package io.github.venompool888.fluidcapsule.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.util.Locale
import java.util.concurrent.Executors

data class SyncDevice(val id: String, val name: String, val platform: String, val publicKey: String, val lastSeenAt: Long)
data class SyncDeviceState(val currentDeviceId: String, val account: String, val devices: List<SyncDevice>, val receiveFrom: Set<String>)
data class SyncCredentials(val server: String, val code: String = "", val username: String = "", val password: String = "")

object SyncEngine {
    // UI, CLI and service pulls share one queue; the blocking long-poll has its own service thread.
    val executor = Executors.newSingleThreadExecutor()

    fun connect(context: Context, credentials: SyncCredentials): SyncDeviceState {
        check(!SyncConfig(context).isConnected) { "请先断开当前连接" }
        val server = SyncApi.normalizeServer(credentials.server)
        check(!SyncConfig(context).requiresHttpApproval(server)) { "请先在短信云同步设置中确认此 HTTP 地址的明文凭据风险" }
        val body = JSONObject().put("deviceName", "FluidCapsule · ${Build.MODEL}")
            .put("platform", "fluidcapsule-android").put("publicKey", SyncCrypto.publicKeyBase64())
        if (credentials.code.isNotBlank()) {
            body.put("enrollmentCode", credentials.code.trim().uppercase(Locale.ROOT))
        } else {
            require(credentials.username.isNotBlank() && credentials.password.isNotEmpty()) { "请输入账号和密码" }
            body.put("username", credentials.username.trim()).put("password", credentials.password)
        }
        val response = SyncApi.forConnect(context, server).post("/api/v1/connect", body)
        require(response.getString("deviceId").isNotBlank() && response.getString("deviceToken").isNotBlank()) { "服务器返回的设备凭据为空" }
        SyncConfig(context).storeConnection(server, response)
        return getDeviceState(context).also { SyncRealtimeService.update(context) }
    }

    fun getDeviceState(context: Context): SyncDeviceState {
        val config = connectedConfig(context)
        val json = SyncApi.forConfig(context, config).get("/api/v1/devices")
        val state = parseState(json)
        require(state.currentDeviceId == config.deviceId) { "服务器返回的设备身份不一致" }
        config.storeState(json, state)
        return state
    }

    fun saveRoutes(context: Context, receiveFrom: Set<String>): SyncDeviceState {
        val config = connectedConfig(context)
        val json = SyncApi.forConfig(context, config).put("/api/v1/sync-routes", JSONObject()
            .put("pushTo", JSONArray()).put("receiveFrom", JSONArray(receiveFrom.toList())))
        val state = parseState(json)
        require(state.currentDeviceId == config.deviceId) { "服务器返回的设备身份不一致" }
        config.storeState(json, state)
        SyncRealtimeService.update(context)
        return state
    }

    fun pull(context: Context): Int {
        val config = connectedConfig(context)
        val state = getDeviceState(context)
        val api = SyncApi.forConfig(context, config)
        val messages = api.get("/api/v1/messages?after=0").optJSONArray("messages") ?: return 0
        val sources = state.devices.associateBy { it.id }
        val processed = config.processedIds()
        var published = 0
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            val deliveryId = message.getLong("deliveryId")
            val eventId = message.getString("eventId")
            if (eventId !in processed) {
                val payload = decryptMessage(config, sources, message)
                if (payload != null) {
                    val source = sources.getValue(message.getString("sourceDeviceId"))
                    try {
                        if (SyncedSmsPublisher.publish(context, eventId, source.name, payload)) published++
                    } catch (error: JSONException) {
                        config.recordError("消息 $eventId 内容无效：${error.message}")
                    } catch (error: IllegalArgumentException) {
                        config.recordError("消息 $eventId 内容无效：${error.message}")
                    }
                }
                config.rememberProcessed(processed, eventId)
            }
            acknowledge(api, deliveryId)
        }
        return published
    }

    private fun decryptMessage(config: SyncConfig, sources: Map<String, SyncDevice>, message: JSONObject): JSONObject? = try {
        val source = sources[message.getString("sourceDeviceId")]
        if (source == null || source.publicKey.isBlank()) throw SecurityException("找不到发送设备的公钥")
        SyncCrypto.decrypt(message.getString("eventId"), config.deviceId, source.publicKey, message.getJSONObject("envelope"))
    } catch (error: Exception) {
        // A poisoned envelope must not block the queue. Keep its diagnostic after acknowledging it.
        config.recordError("消息 ${message.optString("eventId")} 解密失败：${error.message ?: error.javaClass.simpleName}")
        null
    }

    private fun acknowledge(api: SyncApi, deliveryId: Long) {
        try {
            api.post("/api/v1/messages/$deliveryId/ack", JSONObject())
        } catch (error: SyncApi.ApiException) {
            // The previous ack may have reached the server even if its response was lost.
            if (error.status != 404) throw error
        }
    }

    fun disconnect(context: Context) {
        SyncConfig(context).clear()
        context.stopService(Intent(context, SyncRealtimeService::class.java))
    }

    fun status(context: Context): JSONObject {
        val config = SyncConfig(context)
        val state = config.cachedState.takeIf { it.isNotBlank() }?.let { parseState(JSONObject(it)) }
        val names = config.receiveFrom.map { id -> state?.devices?.find { it.id == id }?.name ?: id }
        return JSONObject().put("connected", config.isConnected).put("account", config.account)
            .put("server", config.server).put("deviceId", config.deviceId)
            .put("receiveFrom", JSONArray(names)).put("receiveFromIds", JSONArray(config.receiveFrom.toList()))
            .put("realtimeConnected", SyncRealtimeService.connected).put("lastError", config.lastError)
    }

    fun parseState(json: JSONObject): SyncDeviceState {
        val devices = json.getJSONArray("devices")
        val receiveFrom = json.optJSONArray("receiveFrom") ?: JSONArray()
        return SyncDeviceState(json.getString("currentDeviceId"), json.optString("account"),
            (0 until devices.length()).map { index -> devices.getJSONObject(index).let {
                SyncDevice(it.getString("id"), it.optString("name", "未命名设备"), it.optString("platform"),
                    it.optString("publicKey"), it.optLong("lastSeenAt"))
            } }, (0 until receiveFrom.length()).mapTo(linkedSetOf()) { receiveFrom.getString(it) })
    }

    private fun connectedConfig(context: Context): SyncConfig = SyncConfig(context).also {
        check(it.isConnected) { "尚未连接短信云同步" }
    }
}
