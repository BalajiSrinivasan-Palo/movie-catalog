// Holds the versioned .proto contracts and the generated Java stubs (ADR-004).
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    compileOnly(libs.annotations.api)
}

protobuf {
    protoc { artifact = libs.protobuf.protoc.get().toString() }
    plugins { create("grpc") { artifact = libs.grpc.protoc.gen.get().toString() } }
    generateProtoTasks { all().forEach { task -> task.plugins { create("grpc") } } }
}
