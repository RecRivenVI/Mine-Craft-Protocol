plugins { `java-library` }

group = "io.github.recrivenvi.minecraftprotocol"

version = "0.0.1"

dependencies {
    compileOnly("components:runtime_safety")
    compileOnly(libs.mcp.runtime.gson)
    compileOnly(libs.mcp.netty.http)
    compileOnly(libs.mcp.netty.handler)
    compileOnly(libs.mcp.runtime.log4j)
    testImplementation("components:runtime_safety")
    testImplementation(libs.mcp.runtime.gson)
    testImplementation(libs.mcp.netty.http)
    testImplementation(libs.mcp.netty.handler)
    testImplementation(libs.mcp.runtime.log4j)
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

tasks.test { workingDir = layout.buildDirectory.get().asFile }
