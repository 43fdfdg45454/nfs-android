// The client core: libnfscore.so (ci/core.sh builds it with cargo-ndk from rust/) and its Kotlin
// bindings (UniFFI generates them into src/main/kotlin).
plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.nfsandroid.core"
    buildToolsVersion = "36.1.0"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("net.java.dev.jna:jna:5.17.0@aar")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
