plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.venompool888.fluidcapsule"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.venompool888.fluidcapsule"
        minSdk = 36
        targetSdk = 36
        versionCode = 40
        versionName = "1.4.0"

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

    buildFeatures {
        compose = true
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
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
