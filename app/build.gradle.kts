plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mossling"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mossling"
        minSdk = 26
        targetSdk = 35
        // The release workflow passes the tag and run number so each published APK upgrades the last.
        versionCode = System.getenv("MOSSLING_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("MOSSLING_VERSION_NAME") ?: "0.1.0"
    }

    // Release signing key comes from GitHub secrets (see .github/workflows/release.yml).
    val keystore = System.getenv("MOSSLING_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("MOSSLING_KEY_PASSWORD")
                keyAlias = "mossling"
                keyPassword = System.getenv("MOSSLING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
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
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
