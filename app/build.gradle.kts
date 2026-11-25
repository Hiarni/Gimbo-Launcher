// Plugins
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Android
android {
    namespace = "dev.hiarni.gimbo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hiarni.gimbo.launcher"
        minSdk = 22
        targetSdk = 35
        versionCode = 39
        versionName = "1.15.4"
    }

    // Signatur
    signingConfigs {
        create("gimbo") {
            storeFile = file("gimbo.keystore")
            storePassword = "gimbolauncher"
            keyAlias = "gimbo"
            keyPassword = "gimbolauncher"
        }
    }

    // Build-Typen
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("gimbo")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("gimbo")
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

// Abhängigkeiten
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
