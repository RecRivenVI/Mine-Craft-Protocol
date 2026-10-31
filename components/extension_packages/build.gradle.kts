plugins { java }

val proofSdk by configurations.creating

dependencies {
    proofSdk("components:runtime_core") { targetConfiguration = "extensionSdkElements" }
    proofSdk(libs.mcp.runtime.gson)
}

val platformProbe =
    sourceSets.create("platformProbe") {
        java.srcDir("packages/platform_probe/java")
        compileClasspath = proofSdk
    }

tasks.named<JavaCompile>(platformProbe.compileJavaTaskName) {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(17)
}

val platformProbeJar by
    tasks.registering(Jar::class) {
        from(platformProbe.output)
        archiveBaseName.set("platform-probe")
        destinationDirectory.set(layout.buildDirectory.dir("probe-code"))
    }

val packagePlatformProbe by
    tasks.registering(Exec::class) {
        dependsOn(platformProbeJar)
        workingDir(projectDir)
        commandLine(
            "node",
            "scripts/package-probe.mjs",
            platformProbeJar.get().archiveFile.get().asFile.absolutePath,
        )
    }

tasks.assemble { dependsOn(packagePlatformProbe) }

tasks.check { dependsOn(packagePlatformProbe) }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java { target("src/*/java/**/*.java", "packages/*/java/**/*.java") }
}
