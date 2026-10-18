import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vphone.client"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vphone.client"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        // Release signing is driven by CI environment variables (from GitHub
        // Secrets). Local `assembleRelease` without them produces an unsigned
        // APK; use `assembleDebug` for local installs.
        create("release") {
            val keystoreB64 = System.getenv("VPHONE_KEYSTORE_BASE64")
            if (!keystoreB64.isNullOrBlank()) {
                val keystoreFile = File(project.buildDir, "signing/keystore.jks")
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreB64.trim()))
                storeFile = keystoreFile
                storePassword = System.getenv("VPHONE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VPHONE_KEY_ALIAS")
                keyPassword = System.getenv("VPHONE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("VPHONE_KEYSTORE_BASE64").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
