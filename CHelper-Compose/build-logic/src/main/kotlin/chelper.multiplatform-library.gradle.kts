import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "yancey.chelper.${project.name}"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
