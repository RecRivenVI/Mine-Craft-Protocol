import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    base
    alias(libs.plugins.openapi)
}

val specification = layout.projectDirectory.file("src/main/openapi/minecraft-control-v0.json")

tasks.named<ValidateTask>("openApiValidate") {
    inputSpec.set(specification.asFile.absolutePath)
}

val generateJavaProtocol by
    tasks.registering(GenerateTask::class) {
        generatorName.set("java")
        inputSpec.set(specification.asFile.absolutePath)
        outputDir.set(layout.buildDirectory.dir("generated/java").get().asFile.absolutePath)
        modelPackage.set("io.github.recrivenvi.minecraftprotocol.v0.model")
        globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
        configOptions.set(
            mapOf(
                "dateLibrary" to "java8",
                "hideGenerationTimestamp" to "true",
                "openApiNullable" to "false",
                "sourceFolder" to "src/main/java",
            ),
        )
    }

val generateTypeScriptProtocol by
    tasks.registering(GenerateTask::class) {
        generatorName.set("typescript-fetch")
        inputSpec.set(specification.asFile.absolutePath)
        outputDir.set(layout.buildDirectory.dir("generated/typescript").get().asFile.absolutePath)
        globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
        configOptions.set(
            mapOf(
                "hideGenerationTimestamp" to "true",
                "supportsES6" to "true",
                "typescriptThreePlus" to "true",
            ),
        )
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
    description =
        "Validates V0 and verifies that both generated model families are available at the component path."
    dependsOn("openApiValidate", generateProtocol)
    doLast {
        val javaRoot =
            layout.buildDirectory
                .dir("generated/java/src/main/java/io/github/recrivenvi/minecraftprotocol/v0/model")
                .get()
                .asFile
        val tsRoot = layout.buildDirectory.dir("generated/typescript/models").get().asFile
        for (model in
            listOf(
                "AgentMode",
                "AgentTaskRequest",
                "AgentTaskResponse",
                "ResourceRevisionRef",
                "DeepObservationResponse",
                "ErrorResponse",
                "UiNode",
                "UiSelector",
                "UiDataCategory",
                "UiReadSelector",
                "UiContract",
                "WorldDataCategory",
                "WorldReadContract",
                "WorldUserPlayerResponse",
                "WorldSceneResponse",
                "RuntimeConnection",
            )) {
            check(javaRoot.resolve("$model.java").isFile) { "Missing Java model: $model" }
            check(tsRoot.resolve("$model.ts").isFile) { "Missing TypeScript model: $model" }
        }
        for (model in
            listOf(
                "PlayerStateResponse",
                "BlockStateResponse",
                "EntityQueryResponse",
                "WorldSceneResponse",
                "ProviderReadResponse",
                "StateFrameResponse",
                "RecordingStatusResponse",
            )) {
            check(javaRoot.resolve("$model.java").readText().contains("getDataCategory()")) {
                "Java model drops basic world category: $model"
            }
            check(
                tsRoot
                    .resolve("$model.ts")
                    .readText()
                    .contains("'dataCategory': json['dataCategory']"),
            ) {
                "TypeScript decoder drops basic world category: $model"
            }
        }
        for ((field, getter) in
            listOf(
                "configurationKind" to "getConfigurationKind()",
                "configurationPayloadType" to "getConfigurationPayloadType()",
                "configuredCommandEffect" to "getConfiguredCommandEffect()",
            )) {
            check(javaRoot.resolve("PlayerCommandSubmission.java").readText().contains(getter)) {
                "Java command receipt drops $field"
            }
            check(
                tsRoot
                    .resolve("PlayerCommandSubmission.ts")
                    .readText()
                    .contains("'$field': json['$field']"),
            ) {
                "TypeScript command receipt decoder drops $field"
            }
        }
        for ((field, getter) in
            listOf(
                "properties" to "getProperties()",
                "expectedProperties" to "getExpectedProperties()",
            )) {
            check(javaRoot.resolve("DebugWorldBlockMutation.java").readText().contains(getter)) {
                "Java typed BlockState model drops $field"
            }
            check(
                tsRoot
                    .resolve("DebugWorldBlockMutation.ts")
                    .readText()
                    .contains("'$field': json['$field']"),
            ) {
                "TypeScript typed BlockState decoder drops $field"
            }
        }
        logger.lifecycle(
            "PROTOCOL_COMPONENT_TEST: PASS (schema + Java/TypeScript generation + world category preservation)",
        )
    }
}

tasks.named("check") { dependsOn(test) }
