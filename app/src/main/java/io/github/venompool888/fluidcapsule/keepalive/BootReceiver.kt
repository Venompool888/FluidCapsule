package io.github.venompool888.fluidcapsule.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        io.github.venompool888.fluidcapsule.sync.SyncRealtimeService.update(context)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("keep_alive_enabled", false)) return

        val serviceIntent = Intent(context, KeepAliveService::class.java)
        try {
            context.startForegroundService(serviceIntent)
        } catch (error: IllegalStateException) {
            // Boot and package-replaced are exempt today, but a refusal must not crash the receiver.
            Log.w("FluidCapsuleKeepAlive", "keep-alive start refused type=${error.javaClass.simpleName}")
        }
    }
}
