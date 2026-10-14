plugins { id("net.fabricmc.fabric-loom") }

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
}
