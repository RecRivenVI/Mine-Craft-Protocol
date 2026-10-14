plugins { `java-library` }

group = "io.github.recrivenvi.minecraftprotocol"

version = "0.0.1"

dependencies {
    compileOnly("components:runtime_safety")
    compileOnly(libs.gson)
    compileOnly(libs.mcp.netty.http)
    compileOnly(libs.mcp.netty.handler)
    testImplementation("components:runtime_safety")
    testImplementation(libs.gson)
    testImplementation(libs.mcp.netty.http)
    testImplementation(libs.mcp.netty.handler)
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

tasks.test { workingDir = layout.buildDirectory.get().asFile }
