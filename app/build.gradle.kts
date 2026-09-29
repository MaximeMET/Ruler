plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.openruler.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openruler.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Signed with the local debug keystore so `assembleRelease` produces an
            // installable APK straight away. Replace with your own keystore before
            // publishing anywhere.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Intentionally empty: the app only uses the Android framework, so it builds
    // offline and ships with no third-party code.
}
