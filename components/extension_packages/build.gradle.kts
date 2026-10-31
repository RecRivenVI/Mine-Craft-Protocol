plugins { java }

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/") {
        content { includeGroup("org.spongepowered") }
    }
}

val proofSdk by configurations.creating

dependencies {
    implementation("components:runtime_core")
    implementation(libs.mcp.runtime.gson)
    proofSdk(libs.mixin.processor)
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
    options.compilerArgs.add("-proc:none")
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

val adapterSets =
    listOf("computercraft", "modern_ui", "emi").associateWith { id ->
        sourceSets.create(id.replace("_", "") + "Adapter") {
            java.srcDir("packages/$id/java")
            compileClasspath = proofSdk
        }
    }

val adapterJars = adapterSets.map { (id, sources) ->
    tasks.named<JavaCompile>(sources.compileJavaTaskName) {
        javaCompiler.set(
            javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) },
        )
        options.release.set(17)
        options.compilerArgs.add("-proc:none")
    }
    tasks.register<Jar>(sources.name + "Jar") {
        from(sources.output)
        archiveBaseName.set(id)
        archiveFileName.set("$id.jar")
        destinationDirectory.set(layout.buildDirectory.dir("adapter-code"))
    }
}

dependencies {
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
    testImplementation(adapterSets.getValue("modern_ui").output)
}

tasks.test { useJUnitPlatform() }

val packageAdapters by
    tasks.registering(Exec::class) {
        dependsOn(adapterJars)
        workingDir(projectDir)
        commandLine("node", "scripts/package-adapters.mjs")
    }

tasks.assemble { dependsOn(packageAdapters) }

tasks.check { dependsOn(packageAdapters) }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java { target("src/*/java/**/*.java", "packages/*/java/**/*.java") }
}

val stageValidationLibrary by
    tasks.registering(JavaExec::class) {
        dependsOn(tasks.classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("io.github.recrivenvi.minecraftprotocol.packaging.ExtensionPackageCli")
        args(
            rootDir.parentFile.parentFile.absolutePath,
            providers.gradleProperty("extensionTarget").getOrElse(""),
            providers.gradleProperty("extensionRole").getOrElse("client"),
            providers.gradleProperty("extensionLayout").getOrElse("native"),
        )
    }
