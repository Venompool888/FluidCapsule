package io.github.venompool888.fluidcapsule.keepalive

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore

class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsStore.markAccessibilityConnected(this, true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagnosticsStore.markAccessibilityConnected(this, false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DiagnosticsStore.markAccessibilityConnected(this, false)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
