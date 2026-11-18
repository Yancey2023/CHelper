import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

val composeUiVersion =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("compose-ui")

// These call sites are specific to AndroidX Compose 1.12.1. Recheck them on upgrade.
if (composeUiVersion.orElseThrow().requiredVersion == "1.12.1") {
    pluginManager.withPlugin("com.android.application") {
        extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
            variant.instrumentation.transformClassesWith(
                ComposeTextFieldPerformanceVisitor::class.java,
                InstrumentationScope.ALL,
            ) {}
        }
    }
}
