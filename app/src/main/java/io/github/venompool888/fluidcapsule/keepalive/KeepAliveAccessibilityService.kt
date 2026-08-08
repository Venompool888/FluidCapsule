package io.github.venompool888.fluidcapsule.keepalive

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore

class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsStore.markPublish(this, "ACCESSIBILITY_KEEPALIVE", "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
