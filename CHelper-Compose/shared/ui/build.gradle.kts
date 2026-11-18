plugins {
    id("chelper.multiplatform-library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { androidResources.enable = true }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:data"))
            implementation(libs.multiplatform.navigation.compose)
            implementation(libs.kotlinx.datetime)
            api(libs.compose.multiplatform.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
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
            implementation(libs.ktor.client.mock)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "yancey.chelper.resources"
}
