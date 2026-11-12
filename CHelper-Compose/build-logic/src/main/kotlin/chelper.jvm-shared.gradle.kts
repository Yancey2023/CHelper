import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("chelper.multiplatform-library") }

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Kotlin cannot form a JVM + Android intermediate source set. Both targets compile the
// same physical JVM source directory; dependencies still resolve against each target.
kotlin {
    sourceSets {
        androidMain { kotlin.srcDir("src/jvmSharedMain/kotlin") }
        jvmMain { kotlin.srcDir("src/jvmSharedMain/kotlin") }
        listOf("jvmTest", "androidHostTest").forEach { name ->
            named(name) {
                kotlin.srcDir("src/jvmSharedTest/kotlin")
                dependencies { implementation(catalog.findLibrary("junit").get()) }
            }
        }
    }
}
