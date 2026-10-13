import java.security.MessageDigest

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
        versionCode = 2
        versionName = "0.2.0"

        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                // whisper is unusably slow without optimisation, so the native part is always built as Release
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One stable key signs every build, so each new version installs over the last and keeps the notes.
    // It lives outside the repository (~/.nebo, settings in ~/.gradle/gradle.properties).
    signingConfigs {
        create("nebo") {
            val p = { name: String -> (project.findProperty(name) as String?) }
            storeFile = file(p("NEBO_STORE_FILE") ?: "${System.getProperty("user.home")}/.nebo/nebo-signing.jks")
            storePassword = p("NEBO_STORE_PASSWORD") ?: "android"
            keyAlias = p("NEBO_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = p("NEBO_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("nebo") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("nebo")
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

    packaging {
        jniLibs {
            useLegacyPackaging = false
            // only arm64 phones are built for; the other ABIs would also bring a second libonnxruntime.so
            excludes += listOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
        }
    }
}

kotlin { jvmToolchain(17) }

// sherpa-onnx runs the Parakeet speech model. Its Android library is published only as a release asset,
// so it is fetched once into app/libs (not in git) and checked against a pinned hash.
// This build links ONNX Runtime statically, so it does not clash with the ONNX Runtime the meaning model uses.
val sherpaVersion = "1.13.8"
val sherpaAar = file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    val tmp = File(sherpaAar.path + ".part")
    uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar")
        .toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    val hash = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
    check(hash == "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471") { "sherpa-onnx download has an unexpected hash: $hash" }
    tmp.renameTo(sherpaAar)
}

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
    implementation(files(sherpaAar))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

tasks.withType<Test>().configureEach {
    systemProperty("spm", (project.findProperty("spm") ?: "").toString())
}
