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
        // v2 «Темы» lives next to Margin 3.x as a separate app with its own data.
        applicationId = "ru.anjeib.margin.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "0.20"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static") } }
        // Personal build: pre-fills the OpenRouter key from secrets.properties (not committed anywhere).
        buildConfigField("String", "OPENROUTER_KEY", "\"${secrets.getProperty("OPENROUTER_KEY", "")}\"")
        buildConfigField("String", "YC_KEY", "\"${secrets.getProperty("YC_KEY", "")}\"")
        buildConfigField("String", "YC_FOLDER", "\"${secrets.getProperty("YC_FOLDER", "")}\"")
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
            manifestPlaceholders["appLabel"] = "Margin 2 · тест"
            buildConfigField("boolean", "FORWARD_CHANNELS", "true")
        }
        release {
            manifestPlaceholders["appLabel"] = "Margin 2"
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
            manifestPlaceholders["appLabel"] = "Margin 2 · демо"
            buildConfigField("boolean", "DEMO", "true")
            matchingFallbacks += listOf("release")
        }
    }
    ndkVersion = "27.3.13750724"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
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
    // IMAP for letters forwarded to the user’s own address.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
