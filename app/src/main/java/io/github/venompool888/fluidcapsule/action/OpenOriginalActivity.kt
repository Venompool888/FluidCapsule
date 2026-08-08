package io.github.venompool888.fluidcapsule.action

import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import io.github.venompool888.fluidcapsule.publisher.NotificationFactory

class OpenOriginalActivity : Activity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (handled) return
        handled = true

        getSystemService(NotificationManager::class.java)
            .cancel(NotificationFactory.CAPSULE_NOTIFICATION_ID)

        val original = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT)
        }
        try {
            if (original == null) {
                openSourceApplication(this, intent)
            } else if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        if (Build.VERSION.SDK_INT >= 36) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                        } else {
                            @Suppress("DEPRECATION")
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                }
                original.send(
                    this,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle(),
                )
            } else {
                original.send()
            }
        } catch (_: PendingIntent.CanceledException) {
            openSourceApplication(this, intent)
        } finally {
            finish()
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun openSourceApplication(context: Context, sourceIntent: Intent) {
        val sourcePackage = sourceIntent.getStringExtra(EXTRA_SOURCE_PACKAGE) ?: return
        context.packageManager.getLaunchIntentForPackage(sourcePackage)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    companion object {
        const val ACTION_OPEN_ORIGINAL = "io.github.venompool888.fluidcapsule.action.OPEN_ORIGINAL"
        const val EXTRA_ORIGINAL_INTENT = "original_intent"
        const val EXTRA_SOURCE_PACKAGE = "source_package"
    }
}
