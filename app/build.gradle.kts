import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Release signing config: loaded from keystore.properties when present (written by CI
// from repository secrets). Absent for local dev builds, which fall back to the debug
// key. keystore.properties is gitignored and never committed.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.translator.screen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.translator.screen"
        minSdk = 24
        targetSdk = 35
        versionCode = 15
        versionName = "2.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Dedicated release key, populated only when keystore.properties declares a storeFile
        // (Council P1-12: gate on the property, not just the file's existence, to avoid an NPE
        // if the file is present but empty).
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1+v2+v3 enabled. For Android 7+ (minSdk 24) support this is REQUIRED:
                // v3 alone (Android 9+/API 28+) fails to install on Android 7-8 (API 24-27,
                // which support only v1+v2). AGP 8.5.2 may still emit v3-only despite these
                // flags (verified empirically for minSdk 31), so CI additionally RE-SIGNS
                // each release variant with `apksigner sign --v1-signing-enabled true
                // --v2-signing-enabled true --v3-signing-enabled true` to guarantee all
                // three schemes. CI's verify step then asserts v1+v2+v3=true + the release
                // cert DN + the pinned cert SHA-256. (v1.4.0: minSdk 24 -> Android 7+.)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        // Default debug key (generated on demand by AGP for local dev builds).
        getByName("debug")
    }

    buildTypes {
        release {
            // R8 minification + resource shrinking: removes unused library code (Compose/
            // AndroidX have many classes this app never touches) + shrinks resources, so
            // the release APK is smaller AND library code is obfuscated (harder to reverse).
            // Generous keep rules in proguard-rules.pro (app classes + ML Kit + GMS) make
            // this safe — AGP auto-keeps manifest-referenced classes + applies Compose/
            // AndroidX consumer rules. (build-learnings: family-locator R8 lesson.)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // In CI (CI=true), leave signingConfig null so AGP produces an UNSIGNED,
            // zipaligned release APK; the workflow then zipaligns + apksigner-signs it
            // with v1+v2+v3 (forcing v1 is REQUIRED for Android 7-8 installability —
            // AGP's own signing emits v3-only, and apksigner sign on an already-signed
            // APK doesn't add v1). Locally, sign with the release key (if
            // keystore.properties present) or the debug key. (v1.4.0: minSdk 24.)
            signingConfig = if (System.getenv("CI") == "true") {
                null
            } else if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // ABI splits: produce an arm64-v8a-only APK (~40 MB) for the ~99% of phones that are
    // 64-bit ARM, plus a universal APK (~107 MB, smaller with R8) for 32-bit ARM / x86
    // emulators / rare x86 devices. Both are signed + verified + released. See ROADMAP.md.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty(
                    "robolectric.repository.url",
                    "https://maven.aliyun.com/repository/public",
                )
                test.systemProperty(
                    "robolectric.dependency.cache.dir",
                    "${System.getProperty("user.home")}/.m2/repository",
                )
                test.maxHeapSize = "1024m"
                test.forkEvery = 0L
                test.minHeapSize = "256m"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation("androidx.core:core-splashscreen:1.0.1")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
