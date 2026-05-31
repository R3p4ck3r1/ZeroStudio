@Suppress("JavaPluginLanguageLevel")
plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  alias(libs.plugins.protobuf)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

kotlin {
  jvmToolchain(17)
}

val reapiProtoRoot = layout.projectDirectory.dir("../reapi")

sourceSets {
  main {
    proto {
      srcDir(reapiProtoRoot)
      include("build/bazel/**/*.proto")
    }
  }
}

tasks.jar {
  // Keep the REAPI proto descriptors available to downstream protobuf tasks as
  // import-only protos. This lets modules generate only their own APIs while
  // resolving imports from this compiled project dependency.
  from(reapiProtoRoot) {
    include("build/bazel/**/*.proto")
  }
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
  api(libs.google.protobuf.java)
  api(libs.google.protobuf.kotlin)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.kotlin.stub)
  compileOnly(libs.kotlinx.coroutines.core)
  implementation(libs.grpc.netty.shaded)

  compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobufVersion.get()}"
  }
  plugins {
    create("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.ioGrpcVersion.get()}"
    }
    create("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.ioGrpcStubVersion.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    all().forEach { task ->
      task.plugins {
        create("grpc")
        create("grpckt")
      }
      task.builtins {
        create("kotlin")
      }
    }
  }
}
