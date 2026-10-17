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
    val nettyHttp =
        implementation(target.module("io.netty:netty-codec-http", "netty_http_version"))
            as ExternalModuleDependency
    nettyHttp.isTransitive = false
    nettyHttp.version { strictly("[${target.fact("netty_http_version")}]") }
    add("jarJar", nettyHttp)
}

afterEvaluate {
    dependencies.add(
        "additionalRuntimeClasspath",
        target.module("io.netty:netty-codec-http", "netty_http_version"),
    )
}
