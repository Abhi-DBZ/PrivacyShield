import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// keystore/ is private (not in git). Without it, builds fall back to the debug key.
val keyFile = rootProject.file("keystore/key.properties")
val keyProps = Properties().apply { if (keyFile.exists()) keyFile.inputStream().use { load(it) } }
val hasKey = keyFile.exists()

android {
    namespace = "com.privacyshield.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.privacyshield.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
    }

    signingConfigs {
        if (hasKey) create("privacyshield") {
            storeFile = rootProject.file("keystore/" + keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("privacyshield")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasKey) signingConfigs.getByName("privacyshield") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // ML Kit's face model is native code: one APK per CPU type keeps each one small
    // (App\README.md naming: 64-bit phones / older phones / emulator).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Builds a full Material You colour scheme from one seed colour (the colour picker).
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    // The shield runs as a LifecycleService so CameraX can bind to it.
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    // Front camera frames for the peek alert.
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    // On-device face detection (bundled model: works offline, frames never leave the phone).
    implementation("com.google.mlkit:face-detection:16.1.7")
}
