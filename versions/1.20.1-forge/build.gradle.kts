plugins { id("net.neoforged.moddev.legacyforge") }

dependencies {
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

configurations.named("testCompileClasspath") {
    extendsFrom(configurations.getByName("compileClasspath"))
}

tasks.test {
    useJUnitPlatform()
    classpath += configurations.runtimeClasspath.get()
}

val modId = target.fact("mod_id")

legacyForge { validateAccessTransformers = true }

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
}

tasks.named<Jar>("jar") { manifest.attributes(mapOf("MixinConfigs" to "$modId.mixins.json")) }

dependencies {
    implementation(target.module("io.netty:netty-codec-http", "netty_http_version"))
    annotationProcessor(libs.mixin.processor)
}

configurations.named("annotationProcessor") {
    extendsFrom(configurations.compileClasspath.get())
}

afterEvaluate {
    dependencies.add(
        "additionalRuntimeClasspath",
        target.module("io.netty:netty-codec-http", "netty_http_version"),
    )
}
