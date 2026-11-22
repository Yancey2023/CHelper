import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    id("chelper.jvm-library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The desktop aggregate includes Material by default; this app's UI uses Foundation only.
configurations.configureEach {
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.compose.material3")
}

dependencies {
    implementation(libs.jna.platform)
    implementation(libs.ktor.client.websockets)
    implementation(project(":shared:domain"))
    implementation(project(":shared:data"))
    implementation(project(":shared:ui"))
    implementation(project(":platform:jvm"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.multiplatform.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.navigation.compose)
    testImplementation(libs.compose.multiplatform.ui.test.junit4)
}

val bundledResources = layout.buildDirectory.dir("generated/resources")
val buildNativeCore =
    tasks.register<Exec>("buildNativeCore") {
        description = "Build the Windows x64 JNI bridge and bundle its runtime dependencies"
        workingDir = rootProject.projectDir.parentFile
        commandLine("python", "scripts/build_desktop_core.py")
        inputs.files(
            rootProject.fileTree("../CHelper-Core") {
                include("src/**", "cmake/**", "CMakeLists.txt")
            }
        )
        inputs.file(rootProject.file("../scripts/build_desktop_core.py"))
        inputs.file(rootProject.file("../scripts/native_build_tools.py"))
        outputs.dir(layout.projectDirectory.dir("native/windows-x64"))
        onlyIf { System.getProperty("os.name").startsWith("Windows") }
    }
val prepareResources =
    tasks.register<Sync>("prepareResources") {
        dependsOn(buildNativeCore)
        from(rootProject.file("resources")) {
            include("cpack/**", "old2new/**", "about/**", "rawtext/**")
        }
        from(layout.projectDirectory.dir("native")) { into("native") }
        into(bundledResources)
        doLast {
            val packs = bundledResources.get().file("cpack/index.txt").asFile
            val filenames =
                packs.parentFile
                    .listFiles()
                    .orEmpty()
                    .filter { it.extension == "cpack" }
                    .map { it.name }
                    .sorted()
            check(filenames.isNotEmpty()) { "Missing bundled resource packs" }
            packs.writeText(filenames.joinToString("\n"))
        }
    }

sourceSets.main { resources.srcDir(bundledResources) }

tasks.processResources { dependsOn(prepareResources) }

val installerResources = layout.buildDirectory.dir("generated/windowsInstaller")
val prepareWindowsInstaller =
    tasks.register<Exec>("prepareWindowsInstaller") {
        val javaHome = file(System.getProperty("java.home"))
        commandLine(
            "python",
            rootProject.file("../scripts/prepare_windows_installer.py"),
            "--java-home",
            javaHome,
            "--output",
            installerResources.get().asFile,
        )
        inputs.file(rootProject.file("../scripts/prepare_windows_installer.py"))
        inputs.files(javaHome.resolve("bin/jimage.exe"), javaHome.resolve("lib/modules"))
        outputs.dir(installerResources)
    }

// Compose owns the application image. Its packager replaces --resource-dir, so use jpackage's
// supported --app-image entry point for the two installers that need our protocol component.
if (System.getProperty("os.name").startsWith("Windows")) {
    listOf("Msi" to "msi", "Exe" to "exe").forEach { (suffix, format) ->
        val packageInstaller =
            tasks.register<Exec>("packageWindows$suffix") {
                dependsOn(
                    "createDistributable",
                    prepareWindowsInstaller,
                    rootProject.tasks.named("unzipWix"),
                )
                val image = layout.buildDirectory.dir("compose/binaries/main/app/CHelper")
                val destination = layout.buildDirectory.dir("compose/binaries/main/$format")
                val installer = destination.map { it.file("CHelper-26.2.0.$format") }
                inputs.dir(image)
                inputs.dir(installerResources)
                outputs.file(installer)
                doFirst {
                    destination.get().asFile.mkdirs()
                    val previous = installer.get().asFile
                    // jpackage marks EXE installers read-only; rebuilding must replace that output.
                    if (previous.exists()) previous.setWritable(true)
                    check(!previous.exists() || previous.delete()) { "Unable to replace installer" }
                    environment(
                        "PATH",
                        tasks
                            .named<AbstractJPackageTask>("package$suffix")
                            .get()
                            .wixToolsetDir
                            .get()
                            .asFile
                            .absolutePath + File.pathSeparator + System.getenv("PATH"),
                    )
                    commandLine(
                        file(System.getProperty("java.home") + "/bin/jpackage.exe"),
                        "--type",
                        format,
                        "--app-image",
                        image.get().asFile,
                        "--resource-dir",
                        installerResources.get().asFile,
                        "--dest",
                        destination.get().asFile,
                        "--name",
                        "CHelper",
                        "--app-version",
                        "26.2.0",
                        "--vendor",
                        "Yancey",
                        "--description",
                        "Minecraft Bedrock command helper",
                        "--win-upgrade-uuid",
                        "084945e8-56a1-4af4-94a1-d6f03cdd4d96",
                        "--win-menu",
                        "--win-menu-group",
                        "CHelper",
                        "--win-shortcut",
                        "--win-dir-chooser",
                    )
                }
            }
        tasks.withType<AbstractJPackageTask>().configureEach {
            if (name == "package$suffix") {
                onlyIf("The protocol-aware jpackage installer is built by the dependency") { false }
                dependsOn(packageInstaller)
            }
        }
    }
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "chelper.screenshotsDir",
        rootProject.file("../build/compose-migration/shared-ui").absolutePath,
    )
}

compose.desktop {
    application {
        mainClass = "yancey.chelper.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CHelper"
            packageVersion = "26.2.0"
            description = "Minecraft Bedrock command helper"
            vendor = "Yancey"
            modules(
                "java.desktop",
                "java.logging",
                "java.management",
                "jdk.crypto.ec",
                "jdk.unsupported",
            )
            windows {
                menuGroup = "CHelper"
                shortcut = true
                upgradeUuid = "084945e8-56a1-4af4-94a1-d6f03cdd4d96"
            }
        }
    }
}
