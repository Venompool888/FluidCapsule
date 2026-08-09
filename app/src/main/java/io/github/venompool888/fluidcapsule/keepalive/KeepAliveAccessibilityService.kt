package io.github.venompool888.fluidcapsule.keepalive

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.venompool888.fluidcapsule.diagnostics.DiagnosticsStore
import io.github.venompool888.fluidcapsule.integration.SpeedtestAccessibilityAdapter

class KeepAliveAccessibilityService : AccessibilityService() {
    private val speedtestAdapter by lazy { SpeedtestAccessibilityAdapter(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsStore.markPublish(this, "ACCESSIBILITY_KEEPALIVE", "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SpeedtestAccessibilityAdapter.SPEEDTEST_PACKAGE) return
        speedtestAdapter.inspect(rootInActiveWindow)
    }

    override fun onInterrupt() = Unit
}
