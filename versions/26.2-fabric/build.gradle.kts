import groovy.lang.Closure
import java.util.Properties

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
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

loom {
    splitEnvironmentSourceSets()
    runs {
        named("client") {
            client()
            runDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/client"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client") })
            sourceSet.set("client")
            if (project.hasProperty("mcpQuickPlayServer")) {
                programArguments.addAll("--quickPlayMultiplayer", project.property("mcpQuickPlayServer").toString())
            }
        }
        create("clientMultiplayer") {
            client()
            runDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/client-multiplayer"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client-multiplayer") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client-multiplayer") })
            sourceSet.set("client")
            if (project.hasProperty("mcpQuickPlayServer")) {
                programArguments.addAll("--quickPlayMultiplayer", project.property("mcpQuickPlayServer").toString())
            }
        }
        named("server") {
            server()
            runDirectory.set(rootProject.layout.projectDirectory.dir("instances/$targetId/server"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "server") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "server") })
        }
        create("clientVulkan") {
            client()
            runDirectory.set(rootProject.layout.projectDirectory.dir("validations/.local/vulkan/$targetId"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client") })
            sourceSet.set("client")
            programArguments.addAll("--graphicsBackend", "vulkan")
        }
    }
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    implementation(project(":components:runtime-safety"))
    include(project(":components:runtime-safety"))
    minecraft("com.mojang:minecraft:${targetFact("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${targetFact("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${targetFact("fabric_api_version")}")
}

val metadata = targetFacts.entries.associate { it.key.toString() to it.value.toString() } +
    listOf("mod_id", "mod_name", "mod_license", "mod_version", "mod_authors", "mod_description", "mod_environment")
        .associateWith { projectFact(it) }
tasks.processResources {
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") { expand(metadata + mapOf("fabric_environment" to if (projectFact("mod_environment") == "both") "*" else projectFact("mod_environment"))) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}
