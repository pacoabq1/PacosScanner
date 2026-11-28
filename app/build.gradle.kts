plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.paco.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paco.scanner"
        minSdk = 26
        targetSdk = 35
        // GitHub's automatic build numbers each test build so updates install cleanly.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.2.0"
    }

    signingConfigs {
        // Debug-only key kept in the repo so every test build (GitHub or Android Studio)
        // is signed the same way and installs over the previous one without losing documents.
        getByName("debug") {
            storeFile = file("ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
