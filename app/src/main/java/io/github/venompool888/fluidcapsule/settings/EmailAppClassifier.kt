package io.github.venompool888.fluidcapsule.settings

object EmailAppClassifier {
    private val knownEmailPackages = setOf(
        "com.android.email",
        "com.coloros.email",
        "com.google.android.gm",
        "com.heytap.email",
        "com.microsoft.office.outlook",
        "com.microsoft.outlooklite",
        "com.netease.mail",
        "com.netease.mobimail",
        "com.samsung.android.email.provider",
        "com.tencent.androidqqmail",
        "com.yahoo.mobile.client.android.mail",
    )

    fun isEmailApp(packageName: String, mailtoHandlers: Set<String>): Boolean =
        packageName in mailtoHandlers || packageName in knownEmailPackages
}
