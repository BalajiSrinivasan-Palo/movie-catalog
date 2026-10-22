// Opt-in static analysis and coverage for the Kotlin modules (S-002, NFR-MNT-01, NFR-TEST-02).
//   ./gradlew -I gradle/quality.init.gradle.kts ktlintCheck detekt koverXmlReport     (or: make quality)
// It is an init script on purpose: the plugin versions below were chosen without being able to resolve them, so a
// wrong version fails only this command and never the normal build. Once `make quality` has run green on a real
// machine, move the three plugins into gradle/libs.versions.toml and the module build files (ADR-013) and record
// the result in reports/. NOT VERIFIED until then.
initscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("org.jlleitschuh.gradle:ktlint-gradle:13.1.0")
        classpath("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.1")
        classpath("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.2")
    }
}

val kotlinModules = setOf("service-support", "movie-service", "people-service", "api-gateway")

allprojects {
    if (name in kotlinModules) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "dev.detekt")
        apply(plugin = "org.jetbrains.kotlinx.kover")
        afterEvaluate {
            // Point detekt at the shared config. Done by reflection because this script cannot see the plugin's types;
            // if the plugin's API differs, detekt falls back to its own defaults and says so in its output.
            runCatching {
                val ext = extensions.getByName("detekt")
                val files = ext.javaClass.getMethod("getConfig").invoke(ext) as org.gradle.api.file.ConfigurableFileCollection
                files.setFrom(rootProject.file("config/detekt/detekt.yml"))
            }
        }
    }
}
