plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties

android {
    namespace = "com.neuropocket.app"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.neuropocket.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 27
        versionName = "1.25.0-rc.1"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    signingConfigs {
        create("release") {
            // P0.9: FAIL CLOSED — никаких fallback-паролей в репозитории.
            // Release требует реальный keystore: keystore.properties или env.
            // Debug/unit-тесты работают без него; ошибка — только при сборке release.
            val kp = Properties()
            rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { kp.load(it) }
            val wantsRelease = gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }
            fun req(name: String, env: String): String? {
                val v = kp.getProperty(name) ?: System.getenv(env)
                if (v == null && wantsRelease) throw GradleException(
                    "Release signing not configured: missing $name " +
                        "(keystore.properties or env $env). " +
                        "Debug build works without it; release FAILS CLOSED (P0.9)."
                )
                return v
            }
            val sf = req("storeFile", "NP_STORE_FILE")
            val sp = req("storePassword", "NP_STORE_PASS")
            val ka = req("keyAlias", "NP_KEY_ALIAS")
            val kpw = req("keyPassword", "NP_KEY_PASS")
            if (sf != null && sp != null && ka != null && kpw != null) {
                storeFile = file(sf)
                storePassword = sp
                keyAlias = ka
                keyPassword = kpw
                if (wantsRelease && !storeFile!!.exists()) throw GradleException(
                    "Release keystore file not found: ${storeFile}. " +
                        "Create it per docs/BUILD.md (never commit it)."
                )
            } else if (wantsRelease) {
                throw GradleException("Release signing incomplete (P0.9 fail-closed).")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Лёгкий APK: движок SD (51 МБ) качается из релизов отдельно
        jniLibs {
            excludes += "lib/arm64-v8a/libnpsd.so"
            excludes += "lib/arm64-v8a/libonnxruntime.so"
            excludes += "lib/arm64-v8a/libsherpa-onnx-jni.so"
            excludes += "lib/arm64-v8a/libsherpa-onnx-c-api.so"
            excludes += "lib/arm64-v8a/libsherpa-onnx-cxx-api.so"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(files("libs/sherpa_onnx.aar"))
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
