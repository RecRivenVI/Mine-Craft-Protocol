import groovy.lang.Closure
import java.util.Properties

plugins {
    `java-library`
    id("net.neoforged.moddev.legacyforge") version "2.0.144"
}

val targetFacts = Properties().apply { file("target.properties").reader(Charsets.UTF_8).use { load(it) } }
fun targetFact(key: String): String = requireNotNull(targetFacts.getProperty(key)) { "Missing Target fact: $key" }
fun projectFact(key: String): String = rootProject.providers.gradleProperty(key).get()
val targetId = project.name
val modId = projectFact("mod_id")
val javaVersion = targetFact("java_version").toInt()
version = projectFact("mod_version")
group = projectFact("maven_group")
base { archivesName.set(modId) }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion)) }

@Suppress("UNCHECKED_CAST")
val instanceJvm = rootProject.extra["instanceJvmArgs"] as Closure<List<String>>
@Suppress("UNCHECKED_CAST")
val instanceGame = rootProject.extra["instanceGameArgs"] as Closure<List<String>>

legacyForge {
    version = "${targetFact("minecraft_version")}-${targetFact("forge_version")}"
    validateAccessTransformers = true
    runs {
        create("client") {
            client()
            gameDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/client"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client") })
            if (project.hasProperty("mcpQuickPlayServer")) {
                programArguments.addAll("--quickPlayMultiplayer", project.property("mcpQuickPlayServer").toString())
            }
        }
        create("clientMultiplayer") {
            client()
            gameDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/client-multiplayer"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client-multiplayer") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client-multiplayer") })
            if (project.hasProperty("mcpQuickPlayServer")) {
                programArguments.addAll("--quickPlayMultiplayer", project.property("mcpQuickPlayServer").toString())
            }
        }
        create("server") {
            server()
            gameDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/server"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "server") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "server") })
        }
    }
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
}
tasks.named<Jar>("jar") { manifest.attributes(mapOf("MixinConfigs" to "$modId.mixins.json")) }

dependencies {
    implementation(project(":components:runtime-safety"))
    jarJar(project(":components:runtime-safety"))
    add("additionalRuntimeClasspath", project(":components:runtime-safety"))
    implementation("io.netty:netty-codec-http:${targetFact("netty_http_version")}")
    add("additionalRuntimeClasspath", "io.netty:netty-codec-http:${targetFact("netty_http_version")}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

val metadata = targetFacts.entries.associate { it.key.toString() to it.value.toString() } +
    listOf("mod_id", "mod_name", "mod_license", "mod_version", "mod_authors", "mod_description", "mod_environment")
        .associateWith { projectFact(it) }
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    inputs.properties(metadata)
    expand(metadata)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main { resources.srcDir(generateModMetadata) }
legacyForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}
