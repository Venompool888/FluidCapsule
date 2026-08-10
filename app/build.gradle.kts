plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.venompool888.fluidcapsule"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.venompool888.fluidcapsule"
        minSdk = 36
        targetSdk = 36
        versionCode = 33
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystorePath = providers.environmentVariable("FLUID_CAPSULE_KEYSTORE_PATH").orNull
        val storePassword = providers.environmentVariable("FLUID_CAPSULE_KEYSTORE_PASSWORD").orNull
        val keyAliasValue = providers.environmentVariable("FLUID_CAPSULE_KEY_ALIAS").orNull
        val keyPasswordValue = providers.environmentVariable("FLUID_CAPSULE_KEY_PASSWORD").orNull
        if (listOf(keystorePath, storePassword, keyAliasValue, keyPasswordValue).all { it != null }) {
            create("release") {
                storeFile = file(keystorePath!!)
                this.storePassword = storePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    lint {
        disable += setOf(
            // This is a private, sideloaded CPH2797 utility that intentionally enumerates
            // installed applications and offers explicit keep-alive controls.
            "BatteryLife",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            // API guards are intentionally retained at subsystem boundaries even though
            // the installable 1.0 build is restricted to API 36.
            "ObsoleteSdkInt",
            "OldTargetApi",
            // The current UI is Chinese-only; localization is outside the 1.0 scope.
            "SetTextI18n",
        )
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
