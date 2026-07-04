import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read .env (if present) at build time and expose values to BuildConfig.
// Falls back to empty strings so the project still configures without a .env.
val envFile = rootProject.file(".env")
val envValues: Map<String, String> = if (envFile.exists()) {
    Properties().apply { envFile.inputStream().use { load(it) } }
        .map { (k, v) -> k.toString() to v.toString() }.toMap()
} else {
    emptyMap()
}

val workerUrl = envValues["WORKER_URL"] ?: ""
val postToken = envValues["POST_TOKEN"] ?: ""

android {
    namespace = "dev.darjs.callrelay"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.darjs.callrelay"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "WORKER_URL", "\"$workerUrl\"")
        buildConfigField("String", "POST_TOKEN", "\"$postToken\"")
    }

    buildConfig = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
