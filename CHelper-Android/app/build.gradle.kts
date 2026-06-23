import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "yancey.chelper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "yancey.chelper"
        minSdk = 24
        targetSdk = 37
        versionCode = 83
        versionName = "0.4.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 为了减少软件体积，只兼容arm64-v8a架构
            // abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            // abiFilters.add("x86")
            // abiFilters.add("x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "CHelper调试版")
        }
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "CHelper测试版")
            matchingFallbacks += listOf("release")
        }
    }

    sourceSets.all {
        jniLibs.directories.add("libs")
    }

    // beta 跟 release 用同一份 Umeng / MonitorUtil 实现，免得维护两份。
    // 不能直接走 buildType "继承"——那只继承构建配置，sourceSet 是相互独立的。
    sourceSets {
        getByName("beta") {
            kotlin.directories.add("src/release/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }

    // 单测里调到 android.util.Log 之类 stub 时返回默认值，避免抛 "not mocked" 异常。
    // 仅影响 testDebugUnitTest，对 instrumented test 和运行时无任何作用。
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    ndkVersion = "29.0.14206865"

    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:unchecked")
            options.compilerArgs.add("-Xlint:deprecation")
        }
    }

    val keystorePropertiesFile: File = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("sign") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String

                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs["sign"]
            }
            getByName("beta") {
                signingConfig = signingConfigs["sign"]
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // https://github.com/boxbeam/Crunch
    implementation(libs.crunch)
    // https://github.com/androidx/androidx
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)
    implementation(libs.recyclerview)
    implementation(libs.navigation.compose)
    implementation(libs.datastore)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    // https://github.com/coil-kt/coil
    implementation(libs.coil.compose)
    // https://github.com/Kotlin/kotlinx.serialization
    implementation(libs.kotlinx.serialization.json)
    // https://github.com/square/okhttp
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.logging.interceptor)
    // https://github.com/square/retrofit
    implementation(platform(libs.retrofit.bom))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    // https://github.com/getActivity/DeviceCompat
    implementation(libs.devicecompat)
    // https://github.com/getActivity/XXPermissions
    implementation(libs.xxpermissions)
    // https://github.com/getActivity/Toaster
    implementation(libs.toaster)
    // https://github.com/getActivity/EasyWindow
    implementation(libs.easywindow)
    // https://www.umeng.com
    // 只在 release / beta 引入 Umeng 三件套——debug 不需要崩溃上报，
    // 省下几千个类的 dex 化时间，本地构建快很多。
    // 对应的 MonitorUtil 实现在 src/release/kotlin（debug 用 src/debug/kotlin 的 no-op stub）。
    releaseImplementation(libs.umeng.common)
    releaseImplementation(libs.umeng.asms)
    releaseImplementation(libs.umeng.apm)
    "betaImplementation"(libs.umeng.common)
    "betaImplementation"(libs.umeng.asms)
    "betaImplementation"(libs.umeng.apm)
    // https://github.com/junit-team/junit4
    testImplementation(libs.junit)
    // https://github.com/androidx/androidx
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
