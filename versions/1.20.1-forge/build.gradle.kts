import net.neoforged.moddevgradle.legacyforge.tasks.RemapJar

plugins { id("net.neoforged.moddev.legacyforge") }

dependencies {
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

configurations.named("testCompileClasspath") {
    extendsFrom(configurations.getByName("compileClasspath"))
}

tasks.test {
    workingDir = layout.buildDirectory.get().asFile
    useJUnitPlatform()
    classpath += configurations.runtimeClasspath.get()
}

val modId = target.fact("mod_id")
val probeRefmapName = "$modId.validation.refmap.json"

legacyForge { validateAccessTransformers = true }

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    add(sourceSets.named("probe").get(), probeRefmapName)
    config("$modId.mixins.json")
}

tasks.named<Jar>("jar") { manifest.attributes(mapOf("MixinConfigs" to "$modId.mixins.json")) }

dependencies {
    val nettyHttp =
        implementation(target.module("io.netty:netty-codec-http", "netty_http_version"))
            as ExternalModuleDependency
    nettyHttp.isTransitive = false
    nettyHttp.version { strictly("[${target.fact("netty_http_version")}]") }
    add("jarJar", nettyHttp)
    annotationProcessor(libs.mixin.processor)
    add("probeAnnotationProcessor", libs.mixin.processor)
}

configurations.named("annotationProcessor") {
    extendsFrom(configurations.compileClasspath.get())
}

configurations.named("probeAnnotationProcessor") {
    extendsFrom(configurations.compileClasspath.get())
}

afterEvaluate {
    tasks.named<RemapJar>("reobfJar") {
        // The probe's AP mappings must not become production reobfuscation inputs.
        val productionMappings =
            project.files(remapOperation.mappings.from.toList()).filter {
                it.name != "$probeRefmapName.mappings.tsrg"
            }
        remapOperation.mappings.setFrom(productionMappings)
    }
    dependencies.add(
        "additionalRuntimeClasspath",
        target.module("io.netty:netty-codec-http", "netty_http_version"),
    )
}
