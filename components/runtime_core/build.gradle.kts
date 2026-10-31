plugins { `java-library` }

group = "io.github.recrivenvi.minecraftprotocol"

version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/") {
        content { includeGroup("org.spongepowered") }
    }
}

dependencies {
    compileOnly("components:runtime_safety")
    compileOnly(libs.mixin.processor)
    compileOnly(libs.mcp.asm)
    compileOnly(libs.mcp.asm.tree)
    compileOnly(libs.mcp.runtime.gson)
    compileOnly(libs.mcp.netty.http)
    compileOnly(libs.mcp.netty.handler)
    compileOnly(libs.mcp.runtime.log4j)
    testImplementation("components:runtime_safety")
    testImplementation(libs.mixin.processor)
    testImplementation(libs.mcp.asm)
    testImplementation(libs.mcp.asm.tree)
    testImplementation(libs.mcp.runtime.gson)
    testImplementation(libs.mcp.netty.http)
    testImplementation(libs.mcp.netty.handler)
    testImplementation(libs.mcp.runtime.log4j)
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

tasks.test { workingDir = layout.buildDirectory.get().asFile }

val extensionSdkJar by
    tasks.registering(Jar::class) {
        group = "build"
        description =
            "Builds the Minecraft-independent extension author SDK without Runtime implementation."
        archiveClassifier.set("extension-sdk")
        from(sourceSets.main.get().output) {
            include("io/github/recrivenvi/minecraftprotocol/probe/api/**")
            include("io/github/recrivenvi/minecraftprotocol/probe/extension/**")
        }
    }

tasks.assemble { dependsOn(extensionSdkJar) }

val extensionSdkElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(extensionSdkJar)
}
