plugins { id("net.neoforged.moddev") }

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

dependencies {
    implementation(target.module("io.netty:netty-codec-http", "netty_http_version"))
}

afterEvaluate {
    dependencies.add(
        "additionalRuntimeClasspath",
        target.module("io.netty:netty-codec-http", "netty_http_version"),
    )
}
