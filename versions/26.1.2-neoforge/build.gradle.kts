import groovy.lang.Closure
import java.util.Properties

plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.144"
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

// Vulkan is a validation scenario of the client variant, never a fourth instance type.
val vulkanDirectory = rootProject.layout.projectDirectory.dir("validations/.local/vulkan/$targetId")
val prepareVulkanFmlConfig = tasks.register("prepareVulkanFmlConfig") {
    val config = vulkanDirectory.file("config/fml.toml").asFile
    outputs.file(config)
    doLast {
        config.parentFile.mkdirs()
        val previous = if (config.isFile) config.readText() else ""
        val pattern = Regex("(?m)^\\s*earlyWindowControl\\s*=.*$")
        val next = if (pattern.containsMatchIn(previous)) previous.replace(pattern, "earlyWindowControl = false")
                   else previous + "\nearlyWindowControl = false\n"
        if (next != previous) config.writeText(next)
    }
}

neoForge {
    version = targetFact("neo_version")
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
        create("clientVulkan") {
            client()
            gameDirectory.set(rootProject.layout.projectDirectory.dir("validations/.local/vulkan/$targetId"))
            jvmArguments.addAll(providers.provider { instanceJvm.call(targetId, "client") })
            programArguments.addAll(providers.provider { instanceGame.call(targetId, "client") })
            programArguments.addAll("--graphicsBackend", "vulkan")
            taskBefore(prepareVulkanFmlConfig)
        }
    }
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":components:runtime-safety"))
    jarJar(project(":components:runtime-safety"))
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
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}
