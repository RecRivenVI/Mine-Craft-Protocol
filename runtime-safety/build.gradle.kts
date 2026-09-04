plugins {
    `java-library`
}

// The module is embedded into each target's final mod artifact.  Keep an
// explicit Maven identity so Loader metadata and Jar-in-Jar dependency
// descriptors never fall back to Gradle's "unspecified" version.
group = "io.github.recrivenvi"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}
