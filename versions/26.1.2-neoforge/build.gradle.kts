plugins { id("net.neoforged.moddev") }

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
