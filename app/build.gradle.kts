import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    packaging { resources { excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md") } }
    namespace = "ru.anjeib.margin"
    compileSdk = 35

    defaultConfig {
        // The published app (RuStore, owner's choice 9 Oct). Margin 2 builds up to 0.22 were «ru.anjeib.margin.v2» — a different app.
        applicationId = "ru.margin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 43
        versionName = "0.43"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
        // No keys in what people install: the AI goes through Margin's own door in Yandex Cloud (server/proxy),
        // let in by an invite code. Only the developer's debug build still carries keys from secrets.properties.
        buildConfigField("String", "OPENROUTER_KEY", "\"\"")
        buildConfigField("String", "YC_KEY", "\"\"")
        buildConfigField("String", "YC_FOLDER", "\"\"")
        buildConfigField("String", "PROXY_URL", "\"${secrets.getProperty("PROXY_URL", "")}\"")
        // Closed alpha: one shared code built in, so testers need not type anything (switched off by itself in server/alpha.local).
        buildConfigField("String", "ALPHA_CODE", "\"${secrets.getProperty("ALPHA_CODE", "")}\"")
        buildConfigField("boolean", "DEMO", "false")
        // The developer's own Telegram bot and IMAP: only in debug builds — they need setup and a server-side redo.
        buildConfigField("boolean", "FORWARD_CHANNELS", "false")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the signed release on the same phone — and tells them apart by name.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Margin · тест"
            buildConfigField("boolean", "FORWARD_CHANNELS", "true")
            buildConfigField("String", "OPENROUTER_KEY", "\"${secrets.getProperty("OPENROUTER_KEY", "")}\"")
            buildConfigField("String", "YC_KEY", "\"${secrets.getProperty("YC_KEY", "")}\"")
            buildConfigField("String", "YC_FOLDER", "\"${secrets.getProperty("YC_FOLDER", "")}\"")
        }
        release {
            manifestPlaceholders["appLabel"] = "Margin"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        // A copy to show: opens with two months of the sample designer's life already in it; sits next to Margin 2.
        create("demo") {
            initWith(getByName("release"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            manifestPlaceholders["appLabel"] = "Margin · демо"
            buildConfigField("boolean", "DEMO", "true")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    // The speech engine (sherpa-onnx, ~24 MB) is packed compressed: a smaller download from RuStore, unpacked once on install.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Speech on the phone (GigaAM v3): sherpa-onnx with onnxruntime linked in, from its GitHub release (not in git —
    // fetch it as CLAUDE.md says into third_party/sherpa-onnx); only arm64-v8a is packed (ndk.abiFilters).
    implementation(files("../third_party/sherpa-onnx/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // «Сегодня» on the home screen.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // Optional fingerprint lock.
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 pulls fragment 1.2.5, which rejects the request codes ≥ 65536 that activity 1.10 hands out:
    // every permission request (microphone, calendar, notifications) crashed the release (Fable, 0.35). A current fragment fixes it.
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    // IMAP for letters forwarded to the user’s own address.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
