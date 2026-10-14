plugins { `java-library` }

group = "io.github.recrivenvi.minecraftprotocol"

version = "0.0.1"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }

dependencies {
    compileOnly(libs.gson)
    testImplementation(libs.gson)
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}
