// Root build: shared settings only. Each module declares what it needs.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    group = "com.moviecatalog"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    // JDK 21 is the supported runtime (NFR-TECH-02). The toolchain picks a JDK 21 even when
    // the shell's default java is newer.
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        }
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
