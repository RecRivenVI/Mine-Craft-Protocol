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
        java.srcDir("fixtures/platform_probe/java")
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

val prepareReplacementProbe by
    tasks.registering(Exec::class) {
        workingDir(projectDir)
        inputs.dir("fixtures/platform_probe/java")
        inputs.file("scripts/prepare-replacement.mjs")
        outputs.dir(layout.buildDirectory.dir("replacement-sources"))
        commandLine("node", "scripts/prepare-replacement.mjs")
    }
val replacementProbe =
    sourceSets.create("replacementProbe") {
        java.srcDir(layout.buildDirectory.dir("replacement-sources"))
        compileClasspath = proofSdk
    }

tasks.named<JavaCompile>(replacementProbe.compileJavaTaskName) {
    dependsOn(prepareReplacementProbe)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(17)
    options.compilerArgs.add("-proc:none")
}

val replacementProbeJar by
    tasks.registering(Jar::class) {
        from(replacementProbe.output)
        archiveFileName.set("platform-probe-before.jar")
        destinationDirectory.set(layout.buildDirectory.dir("probe-code"))
    }
val packageReplacementProbe by
    tasks.registering(Exec::class) {
        dependsOn(replacementProbeJar)
        workingDir(projectDir)
        commandLine(
            "node",
            "scripts/package-probe.mjs",
            replacementProbeJar.get().archiveFile.get().asFile.absolutePath,
            "--replacement-before",
        )
    }

tasks.check { dependsOn(packageReplacementProbe) }

val adapterIds =
    (groovy.json.JsonSlurper().parse(file("extensionpacks/index.json")) as List<*>).map {
        it.toString()
    }
val prepareAuthoring by
    tasks.registering(Exec::class) {
        workingDir(projectDir)
        inputs.dir("authoring")
        inputs.file("extensionpacks/index.json")
        inputs.files(adapterIds.map { file("extensionpacks/$it/package.json") })
        outputs.dir(layout.buildDirectory.dir("authoring"))
        commandLine("node", "scripts/prepare-authoring.mjs")
    }
val adapterSets = adapterIds.associateWith { id ->
    sourceSets.create(id.replace("_", "") + "Adapter") {
        java.srcDir("extensionpacks/$id/java")
        if (id in listOf("ae2", "create", "exposure", "littletiles"))
            java.srcDir(layout.buildDirectory.dir("authoring/$id"))
        compileClasspath = proofSdk
    }
}

val adapterJars = adapterSets.map { (id, sources) ->
    tasks.named<JavaCompile>(sources.compileJavaTaskName) {
        if (id in listOf("ae2", "create", "exposure", "littletiles")) dependsOn(prepareAuthoring)
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
    testImplementation(adapterSets.getValue("modernui").output)
    testImplementation(adapterSets.getValue("create").output)
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

val prepareLifecycleFixtures by
    tasks.registering(Exec::class) {
        workingDir(projectDir)
        inputs.dir("fixtures/lifetime_probe")
        outputs.dir(layout.buildDirectory.dir("lifecycle-sources"))
        commandLine("node", "scripts/prepare-lifecycle-fixtures.mjs")
    }
val lifecycleFixtures =
    sourceSets.create("lifecycleFixtures") {
        java.srcDir(layout.buildDirectory.dir("lifecycle-sources"))
        compileClasspath = proofSdk
    }

tasks.named<JavaCompile>(lifecycleFixtures.compileJavaTaskName) {
    dependsOn(prepareLifecycleFixtures)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(17)
    options.compilerArgs.add("-proc:none")
}

val lifecycleJars =
    listOf("left", "right").map { side ->
        tasks.register<Jar>("lifetime" + side.replaceFirstChar(Char::uppercaseChar) + "Jar") {
            from(lifecycleFixtures.output) {
                include("io/github/recrivenvi/minecraftprotocol/extensions/lifetime_$side/**")
            }
            archiveFileName.set("lifetime_$side.jar")
            destinationDirectory.set(layout.buildDirectory.dir("lifecycle-code"))
        }
    }
val packageLifecycleFixtures by
    tasks.registering(Exec::class) {
        dependsOn(lifecycleJars)
        workingDir(projectDir)
        commandLine("node", "scripts/package-lifecycle-fixtures.mjs")
    }

tasks.assemble { dependsOn(packageLifecycleFixtures) }

tasks.check { dependsOn(packageLifecycleFixtures) }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java { target("src/*/java/**/*.java", "extensionpacks/*/java/**/*.java") }
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
