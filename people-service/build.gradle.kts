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
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5") // kotlin.test assertions in tests
    testImplementation(libs.grpc.inprocess)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") } }

// Run the tests in a zone with an old local-mean-time offset (+06:55:25 before 1905), so date handling that
// goes through the JVM's time zone fails here as it did on the owner's machine, not only there.
tasks.withType<Test>().configureEach { systemProperty("user.timezone", "Asia/Singapore") }
