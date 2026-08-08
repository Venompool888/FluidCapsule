package io.github.venompool888.fluidcapsule.action

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import io.github.venompool888.fluidcapsule.publisher.PublisherRouter
import io.github.venompool888.fluidcapsule.settings.UserSettings

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_OTP) return
        val otp = intent.getStringExtra(EXTRA_OTP)?.takeIf { it.matches(Regex("[0-9A-Za-z]{4,12}")) }
            ?: return

        val clip = ClipData.newPlainText("验证码", otp)
        if (Build.VERSION.SDK_INT >= 33 && UserSettings.maskOtpClipboardPreview(context)) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        PublisherRouter.publishCopiedFeedback(context)
    }

    companion object {
        const val ACTION_COPY_OTP = "io.github.venompool888.fluidcapsule.action.COPY_OTP"
        const val EXTRA_OTP = "otp"
    }
}
