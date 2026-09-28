plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.nfsandroid"
    buildToolsVersion = "36.1.0"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nfsandroid"
        minSdk = 33
        targetSdk = 36
        // From GitVersion in the CI (ci/version.sh): each commit on master is a newer version, so a
        // release signed with the same key installs over the last.
        versionCode = System.getenv("NFS_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("NFS_VERSION") ?: "0.0.0-local"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing from the CI's secrets (NFS_KEYSTORE_BASE64 decoded to release.jks); without
    // them, the debug key, so that a release can still be built and tried.
    signingConfigs {
        create("release") {
            val keystore = rootProject.file("release.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("NFS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NFS_KEY_ALIAS") ?: "nfs"
                keyPassword = System.getenv("NFS_KEYSTORE_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // Android lint's security checks fail the release build (lintVital runs with it): exported
    // components without permission, trust or hostname checks turned off, world-readable files,
    // backups, debuggable releases, intents that launch what an app hands over.
    lint {
        fatal += setOf(
            "AllowBackup", "BadHostnameVerifier", "ExportedContentProvider", "ExportedReceiver",
            "ExportedService", "GrantAllUris", "HardcodedDebugMode", "InsecureBaseConfiguration",
            "PackagedPrivateKey", "SetWorldReadable", "SetWorldWritable", "TrustAllX509TrustManager",
            "UnsafeIntentLaunch", "UnsafeProtectedBroadcastReceiver", "WorldReadableFiles",
            "WorldWriteableFiles", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
