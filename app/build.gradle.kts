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
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // The whole point of the rewrite is to stay tiny: R8 strips the unused
            // Kotlin/Android plumbing and the resource shrinker drops every string,
            // colour and layout the app never touches.
            isMinifyEnabled = true
            isShrinkResources = true
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

    packaging {
        resources {
            // Kotlin ships reflection metadata (`*.kotlin_builtins`, `*.kotlin_module`)
            // that only kotlin-reflect and the compiler read. This app has neither.
            excludes += setOf(
                "kotlin/**",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    // Intentionally empty: the app only uses the Android framework, so it builds
    // offline and ships with no third-party code.
}
