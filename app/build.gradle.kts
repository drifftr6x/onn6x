plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.onnlauncher"
    compileSdk = 34

    defaultConfig {
        // Fork branch: its own app ID, so it installs next to the main launcher instead of replacing it.
        applicationId = "com.example.onnlauncher.fork"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            // Smaller, faster app: R8 shrinks and optimizes the code, unused resources are dropped.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the same debug key as your current install, so "adb install -r" updates it in place
            // and keeps all your settings. (Fine for a box at home; a Play Store app would use its own key.)
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    // Web requests for the NAS wallpaper source (WebDAV needs methods the built-in client refuses).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Icons for the Quick settings tiles. R8 strips every icon we don't use from the release build.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
}
