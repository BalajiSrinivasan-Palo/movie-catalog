// Small shared library for the gRPC services: server lifecycle, correlation id propagation and the
// error helper that builds `google.rpc.Status` with `common.v1.ErrorDetail` (ADR-005).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
    `java-library`
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}") }
}

dependencies {
    api(project(":contracts"))
    api(libs.grpc.netty.shaded)
    api(libs.grpc.services)
    api("org.springframework:spring-context")
    api("org.slf4j:slf4j-api")

    testImplementation(kotlin("test"))
    testImplementation(libs.grpc.inprocess)
    testRuntimeOnly("ch.qos.logback:logback-classic") // real SLF4J MDC in tests; slf4j-api alone is a no-op
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") } }
