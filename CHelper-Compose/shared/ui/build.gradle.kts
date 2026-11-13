plugins {
    id("chelper.jvm-shared")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        listOf(androidMain, jvmMain).forEach { sourceSet ->
            sourceSet.dependencies {
                api(project(":shared:data"))
                implementation(project(":shared:native"))
                implementation(libs.multiplatform.navigation.compose)
            }
        }

        commonMain.dependencies {
            api(libs.compose.multiplatform.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            api(project(":shared:domain"))
            implementation(libs.multiplatform.lifecycle.viewmodel.compose)
            implementation(libs.multiplatform.lifecycle.runtime.compose)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.ui.tooling.preview)
            api(libs.navigation.event.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "yancey.chelper.resources"
}
