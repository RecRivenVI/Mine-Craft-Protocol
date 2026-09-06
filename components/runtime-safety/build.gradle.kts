plugins {
    `java-library`
}

// The module is embedded into each target's final mod artifact.  Keep an
// explicit Maven identity so Loader metadata and Jar-in-Jar dependency
// descriptors never fall back to Gradle's "unspecified" version.
group = providers.gradleProperty("maven_group").get()
version = "0.0.1"

repositories { mavenCentral() }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}
tasks.test { useJUnitPlatform() }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}
