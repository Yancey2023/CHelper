plugins {
    id("chelper.jvm-shared")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        listOf(androidMain, jvmMain).forEach { sourceSet ->
            sourceSet.dependencies {
                api(project.dependencies.platform(libs.okhttp.bom))
                api(libs.okhttp)
                implementation(libs.okhttp.brotli)
                implementation(libs.logging.interceptor)
                api(project.dependencies.platform(libs.retrofit.bom))
                api(libs.retrofit)
                implementation(libs.retrofit.converter.kotlinx.serialization)
            }
        }
        commonMain.dependencies {
            api(libs.datastore.core)
            api(libs.datastore.core.okio)
            api(project(":shared:domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
