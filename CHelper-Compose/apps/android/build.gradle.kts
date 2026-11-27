import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("chelper.android-text-field-performance")
}

val generatedJni = rootProject.file("../build/artifacts/android/jni")
val generatedResources = rootProject.file("../build/artifacts/resources")
val useLocalBaselineProfile = providers.gradleProperty("localBaselineProfile").orNull == "true"

android {
    namespace = "yancey.chelper"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "yancey.chelper"
        buildConfigField("boolean", "PERFORMANCE_AUTOMATION", "false")
        buildConfigField(
            "boolean",
            "PERFORMANCE_COLLECTION",
            providers.gradleProperty("collectFrameMetrics").orElse("false").get(),
        )
        minSdk = 24
        targetSdk = 37
        versionCode = 87
        versionName = "26.2.0"

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
                "proguard-rules.pro",
            )
        }
        debug {
            buildConfigField("boolean", "PERFORMANCE_COLLECTION", "true")
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

    sourceSets.getByName("main").apply {
        jniLibs.directories.add(generatedJni.path)
        assets.directories.add(generatedResources.path)
    }
    // The NDK build task already strips this library before handing it to Gradle.
    packaging.jniLibs.keepDebugSymbols.add("**/libCHelperAndroid.so")
    // Native manifest/host R references also use these assets. Compose packages its own assets
    // through shared:ui's androidResources support; this AAPT mapping does not replace it.
    sourceSets
        .getByName("main")
        .res
        .directories
        .add(rootProject.file("shared/ui/src/commonMain/composeResources").path)

    // beta 跟 release 用同一份 Umeng / MonitorUtil 实现，免得维护两份。
    // 不能直接走 buildType "继承"——那只继承构建配置，sourceSet 是相互独立的。
    sourceSets { getByName("beta") { kotlin.directories.add("src/release/kotlin") } }

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
    testOptions { unitTests.isReturnDefaultValues = true }

    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:unchecked")
            options.compilerArgs.add("-Xlint:deprecation")
        }
    }

    val keystorePropertiesFile: File = rootProject.file("keystore.properties")
    if (
        keystorePropertiesFile.exists() &&
        providers.gradleProperty("unsignedRelease").orNull != "true"
    ) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("sign") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                val configuredPath = keystoreProperties["storeFile"] as String
                val currentFile = file(configuredPath)
                val legacyFile = rootProject.file("androidApp").resolve(configuredPath).normalize()
                // Preserve parent-relative signing paths from the former androidApp directory.
                storeFile = if (legacyFile.isFile) legacyFile else currentFile
                storePassword = keystoreProperties["storePassword"] as String

                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs["sign"] }
            getByName("beta") { signingConfig = signingConfigs["sign"] }
        }
    }
}

androidComponents.onVariants { variant ->
    val taskSuffix = variant.name.replaceFirstChar(Char::uppercaseChar)
    tasks.named { it == "merge${taskSuffix}JniLibFolders" }.configureEach {
        inputs.file(generatedJni.resolve("arm64-v8a/libCHelperAndroid.so"))
            .withPropertyName("chelperJniLibrary")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    tasks.named { it == "merge${taskSuffix}Assets" }.configureEach {
        inputs.dir(generatedResources.resolve("cpack"))
            .withPropertyName("chelperResourcePacks")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(generatedResources.resolve("old2new/old2new.dat"))
            .withPropertyName("chelperBlockConversionData")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

// Apply after the profile plugin copies release configuration and sources to its variants.
androidComponents.finalizeDsl { extension ->
    extension.buildTypes
        .filter {
            it.name.startsWith("benchmark") || it.name.startsWith("nonMinified")
        }
        .forEach { type ->
            type.applicationIdSuffix = ".benchmark"
            type.signingConfig = extension.signingConfigs.getByName("debug")
            type.buildConfigField("boolean", "PERFORMANCE_AUTOMATION", "true")
            type.buildConfigField(
                "boolean",
                "PERFORMANCE_COLLECTION",
                providers.gradleProperty("collectFrameMetrics").orElse("false").get(),
            )
            extension.sourceSets.getByName(type.name).apply {
                kotlin.directories.clear()
                kotlin.directories.addAll(listOf("src/benchmark/kotlin", "src/debug/kotlin"))
                manifest.srcFile("src/benchmark/AndroidManifest.xml")
            }
        }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

baselineProfile {
    // Without a producer dependency the plugin wires an empty generated input in ordinary builds.
    automaticGenerationDuringBuild = !useLocalBaselineProfile
    saveInSrc = useLocalBaselineProfile
    baselineProfileOutputDir = "../../local-baseline-profile"
    mergeIntoMain = true
    warnings { variantHasNoBaselineProfileDependency = useLocalBaselineProfile }
    filter {
        exclude("yancey.chelper.android.benchmark.**")
        exclude("io.ktor.client.engine.mock.**")
        exclude("androidx.compose.runtime.tracing.**")
        exclude("androidx.tracing.perfetto.**")
    }
}

dependencies {
    implementation(libs.profileinstaller)
    implementation(libs.metrics.performance)
    if (useLocalBaselineProfile) {
        baselineProfile(project(":apps:benchmark"))
    }
    implementation(project(":shared:domain"))
    implementation(project(":shared:data"))
    implementation(project(":shared:ui"))
    implementation(project(":platform:jvm"))
    // https://github.com/boxbeam/Crunch
    implementation(libs.crunch)
    // https://github.com/androidx/androidx
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)
    implementation(libs.recyclerview)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // Material is used by tooling's parameterized-preview launcher, not our parameterless previews.
    debugImplementation(libs.compose.ui.tooling) {
        exclude(group = "androidx.compose.material")
        exclude(group = "androidx.compose.material3")
    }
    // https://github.com/coil-kt/coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    // https://github.com/Kotlin/kotlinx.serialization
    implementation(libs.kotlinx.serialization.json)
    // https://github.com/square/retrofit
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
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// The Baseline Profile plugin creates these configurations after the Android DSL is evaluated.
configurations.configureEach {
    if (name.startsWith("benchmark") || name.startsWith("nonMinified")) {
        exclude(group = "com.umeng.umsdk")
    }
    if (
        name.endsWith("Implementation") &&
        (name.startsWith("benchmark") || name.startsWith("nonMinified"))
    ) {
        dependencies.add(project.dependencies.create(libs.ktor.client.mock.get()))
        dependencies.add(project.dependencies.create(libs.compose.runtime.tracing.get()))
        // Load the packaged read-only binary on Android 17 instead of a writable code_cache copy.
        dependencies.add(project.dependencies.create(libs.tracing.perfetto.binary.get()))
    }
}
