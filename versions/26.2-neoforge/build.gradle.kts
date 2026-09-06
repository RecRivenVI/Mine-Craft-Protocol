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
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("io.netty:netty-codec-http:4.2.15.Final")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}

val metadata = targetFacts.entries.associate { it.key.toString() to it.value.toString() } +
    listOf("mod_id", "mod_name", "mod_license", "mod_version", "mod_authors", "mod_description", "mod_environment")
        .associateWith { projectFact(it) }
tasks.processResources {
    inputs.properties(metadata)
    filesMatching("META-INF/neoforge.mods.toml") { expand(metadata) }
}

// Test-only mapped Minecraft classpath, unchanged from the previous target-local gate.
configurations.named("testCompileClasspath") { extendsFrom(configurations.getByName("compileClasspath")) }
tasks.test {
    useJUnitPlatform()
    classpath += configurations.runtimeClasspath.get()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}
