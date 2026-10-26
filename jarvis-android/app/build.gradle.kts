plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "2.6"

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Même clé de signature à chaque compilation : l'appli s'installe par-dessus
    // l'ancienne version et garde ses réglages (clés API comprises).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("jarvis-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("com.anthropic:anthropic-java:3.2.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
