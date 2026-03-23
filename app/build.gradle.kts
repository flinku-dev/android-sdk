import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.flinku.testapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.flinku.testapp"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val apiKey = localProperties.getProperty("flinku.api.key", "") ?: ""
        val baseUrl = localProperties.getProperty("flinku.base.url", "http://159.65.159.159:3001") ?: ""
        buildConfigField("String", "FLINKU_API_KEY", "\"${apiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "FLINKU_BASE_URL", "\"${baseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":flinku-sdk"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
