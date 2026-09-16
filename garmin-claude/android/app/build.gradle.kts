import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Local, gitignored config -- see /android/secrets.properties.example.
// Never commit secrets.properties itself.
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties()
if (secretsFile.exists()) {
    secrets.load(FileInputStream(secretsFile))
}
fun secret(key: String, default: String = ""): String =
    (System.getenv(key) ?: secrets.getProperty(key, default))

android {
    namespace = "com.gavinb8.askclaude"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gavinb8.askclaude"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Dev-only default so the app builds out of the box; override per
        // README instructions instead of editing this file.
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${secret("ANTHROPIC_API_KEY")}\"")
        buildConfigField("String", "CLAUDE_MODEL", "\"${secret("CLAUDE_MODEL", "claude-sonnet-5")}\"")
        buildConfigField("String", "BRIDGE_SERVER_URL", "\"${secret("BRIDGE_SERVER_URL", "")}\"")
        buildConfigField("String", "BRIDGE_SHARED_SECRET", "\"${secret("BRIDGE_SHARED_SECRET", "")}\"")
        buildConfigField("String", "GARMIN_APP_ID", "\"d586b178-b05c-49de-b6cd-11db8fdf9170\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Connect IQ Mobile/Companion SDK for Android (talks to Garmin Connect
    // Mobile over IPC, which in turn talks to the watch over Bluetooth LE).
    // If this coordinate has moved by the time you build, check
    // https://mvnrepository.com/artifact/com.garmin.connectiq for the
    // current version and update it here.
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0@aar")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
