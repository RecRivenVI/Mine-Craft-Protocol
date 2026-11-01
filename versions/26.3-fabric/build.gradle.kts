plugins { id("net.fabricmc.fabric-loom") }

loom { accessWidenerPath = file("src/main/resources/mine_craft_protocol.accesswidener") }

dependencies {
    testImplementation(libs.mcp.junit)
    testRuntimeOnly(libs.mcp.junit.launcher)
}

configurations.named("testCompileClasspath") {
    extendsFrom(configurations.getByName("clientCompileClasspath"))
}

tasks.test {
    workingDir = layout.buildDirectory.get().asFile
    useJUnitPlatform()
    classpath += configurations.getByName("clientRuntimeClasspath")
    classpath += sourceSets["client"].output
}

sourceSets.test {
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}

dependencies {
    implementation(target.module("net.fabricmc.fabric-api:fabric-api", "fabric_api_version"))
    include(target.module("net.fabricmc.fabric-api:fabric-api", "fabric_api_version"))
}

repositories {
    maven("https://api.modrinth.com/maven") {
        content { includeModule("maven.modrinth", "modmenu") }
    }
}

dependencies { "clientCompileOnly"(target.module("maven.modrinth:modmenu", "modmenu_version")) }
