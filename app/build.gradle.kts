plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.venompool888.fluidcapsule"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.venompool888.fluidcapsule"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.6.0-dev"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
