import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "yancey.chelper.benchmark.test"
    compileSdk { version = release(37) }
    targetProjectPath = ":apps:android"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants { it.enable = it.buildType in setOf("nonMinifiedRelease", "benchmarkRelease") }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.benchmark.macro)
    implementation(libs.tracing.perfetto.binary)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}
