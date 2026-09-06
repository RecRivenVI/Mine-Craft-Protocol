pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Mine-Craft-Protocol"

fun includeTarget(id: String) {
    val directory = file("versions/$id")
    require(directory.isDirectory && directory.resolve("build.gradle.kts").isFile && directory.resolve("target.properties").isFile) {
        "Missing real Target: $id"
    }
    include(":versions:$id")
}

fun includeComponent(id: String) {
    val directory = file("components/$id")
    require(directory.isDirectory && directory.resolve("build.gradle.kts").isFile) { "Missing Gradle component: $id" }
    include(":components:$id")
}

includeComponent("protocol-schema")
includeComponent("runtime-safety")
includeTarget("1.20.1-forge")
includeTarget("1.21.1-neoforge")
includeTarget("26.1.2-neoforge")
includeTarget("26.2-neoforge")
includeTarget("26.2-fabric")
