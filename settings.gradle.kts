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

include(":protocol-schema")
include(":runtime-safety")
include(":versions:1.20.1-forge")
include(":versions:1.21.1-neoforge")
include(":versions:26.1.2-neoforge")
include(":versions:26.2-neoforge")
include(":versions:26.2-fabric")
