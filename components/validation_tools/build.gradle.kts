val verifyControl by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/conformance-agent_control/task/Invoke-ControlImplementationStaticGate.ps1",
        )
    }
val verifyShowcase by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/feature-control_ui_showcase/task/Test-ControlUiShowcase.ps1",
        )
    }

val verifyVanillaUiTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/conformance-vanilla_ui/task/Test-VanillaUiSuite.ps1",
        )
    }

val verifyWorldReadTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "components/validation_tools/conformance-world_read/task/test-world-read.mjs",
        )
    }

tasks.check { dependsOn(verifyControl, verifyShowcase, verifyVanillaUiTools, verifyWorldReadTools) }
