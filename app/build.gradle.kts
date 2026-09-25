plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.infusory.modelviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.infusory.modelviewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // The five sample .glb files are bundled under assets/models. We keep
    // them UNCOMPRESSED in the APK (aaptOptions.noCompress) so gltfio can
    // read them straight out of the asset stream without paying an extra
    // inflate cost when a model is added at runtime.
    androidResources {
        noCompress += "glb"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Filament (raw engine, not the SceneView wrapper) ---
    // We talk to Filament + gltfio directly rather than a wrapper library:
    // it gives us a single shared Engine/Renderer/UbershaderProvider across
    // every model container, and full control over Viewports so five
    // independently-draggable "windows" can share ONE SurfaceView/SwapChain
    // instead of five overlapping SurfaceViews (see README, "Rendering
    // architecture").
    val filamentVersion = "1.51.0"
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:gltfio-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
}
