import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    base
    id("org.openapi.generator") version "7.25.0"
}

val specification = layout.projectDirectory.file("src/main/openapi/minecraft-control-v0.json")

tasks.named<ValidateTask>("openApiValidate") {
    inputSpec.set(specification.asFile.absolutePath)
}

val generateJavaProtocol by tasks.registering(GenerateTask::class) {
    generatorName.set("java")
    inputSpec.set(specification.asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/java").get().asFile.absolutePath)
    modelPackage.set("io.github.recrivenvi.minecraftprotocol.v0.model")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
    configOptions.set(mapOf(
        "dateLibrary" to "java8",
        "hideGenerationTimestamp" to "true",
        "openApiNullable" to "false",
        "sourceFolder" to "src/main/java"
    ))
}

val generateTypeScriptProtocol by tasks.registering(GenerateTask::class) {
    generatorName.set("typescript-fetch")
    inputSpec.set(specification.asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/typescript").get().asFile.absolutePath)
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
    configOptions.set(mapOf(
        "hideGenerationTimestamp" to "true",
        "supportsES6" to "true",
        "typescriptThreePlus" to "true"
    ))
}

val generateProtocol by tasks.registering {
    group = "build"
    description = "Generates the unstable V0 Java and TypeScript protocol models."
    dependsOn(generateJavaProtocol, generateTypeScriptProtocol)
}

tasks.named("assemble") {
    dependsOn(generateProtocol)
}

val test by tasks.registering {
    group = "verification"
    description = "Validates V0 and verifies that both generated model families are available at the component path."
    dependsOn("openApiValidate", generateProtocol)
    doLast {
        val javaRoot = layout.buildDirectory.dir("generated/java/src/main/java/io/github/recrivenvi/minecraftprotocol/v0/model").get().asFile
        val tsRoot = layout.buildDirectory.dir("generated/typescript/models").get().asFile
        for (model in listOf("AgentMode", "ResourceRevisionRef", "DeepObservationResponse", "ErrorResponse")) {
            check(javaRoot.resolve("$model.java").isFile) { "Missing Java model: $model" }
            check(tsRoot.resolve("$model.ts").isFile) { "Missing TypeScript model: $model" }
        }
        logger.lifecycle("PROTOCOL_COMPONENT_TEST: PASS (schema + Java/TypeScript generation)")
    }
}
tasks.named("check") { dependsOn(test) }
