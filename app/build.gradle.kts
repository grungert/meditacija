plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.grungert.nebo"
    compileSdk { version = release(37) }
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.grungert.nebo"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                // whisper is unusably slow without optimisation, so the native part is always built as Release
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources { noCompress += listOf("onnx", "bin", "model") }

    packaging { jniLibs { useLegacyPackaging = false } }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

tasks.withType<Test>().configureEach {
    systemProperty("spm", (project.findProperty("spm") ?: "").toString())
}
