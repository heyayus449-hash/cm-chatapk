plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.cmchat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.cmchat.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// A transitive dependency (tor-android) pulls kotlin-stdlib 2.3.0, whose
// metadata our Kotlin 2.1.0 compiler can't read. Pin stdlib to 2.1.0; its
// public API is a subset so tor-android still links and runs.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Crypto: libsodium via lazysodium (Argon2id + secretbox). No hand-rolled crypto.
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")

    // Vault serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Tor: Guardian Project tor-android (bundles the tor binary) + jtorctl
    // control-port library. All networking goes through Tor.
    implementation("info.guardianproject:tor-android:0.4.9.5")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Unit tests run on the host JVM; lazysodium-java bundles a desktop
    // libsodium so the same CryptoManager/Vault code is testable in CI.
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.goterl:lazysodium-java:5.2.0")
    testImplementation("net.java.dev.jna:jna:5.19.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
