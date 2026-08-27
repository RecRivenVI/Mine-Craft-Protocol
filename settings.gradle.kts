pluginManagement {
    includeBuild("components/configuration")
    includeBuild("components/conventions")
    includeBuild("components/compliance")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("io.github.recrivenvi.configuration")
    id("io.github.recrivenvi.conventions")
    id("io.github.recrivenvi.compliance")
}

rootProject.name = "Mine-Craft-Protocol"

targets {
    register("1.20.1-forge")
    register("1.21.1-neoforge")
    register("26.1.2-neoforge")
    register("26.3-neoforge")
    register("26.3-fabric")
}

components {
    product("runtime_safety")
    tool("protocol_schema")
    tool("companion")
    tool("validation_tools")
}
