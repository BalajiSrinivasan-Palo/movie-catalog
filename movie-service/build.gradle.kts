plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":service-support"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc") // hosts Actuator health only
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.imageio.webp)
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.grpc.inprocess)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5") // kotlin.test assertions in tests
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") } }

// The streaming proof (S-301 AC5/AC6) runs in its own JVM limited to 128 MB, so a 5 MiB upload that was
// buffered or fully decoded would fail. Everything else runs in the normal test task.
tasks.test {
    useJUnitPlatform { excludeTags("streaming") }
}

val streamingTest by tasks.registering(Test::class) {
    description = "Runs the memory-bounded artwork streaming tests with -Xmx128m."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("streaming") }
    maxHeapSize = "128m"
}

tasks.check { dependsOn(streamingTest) }
