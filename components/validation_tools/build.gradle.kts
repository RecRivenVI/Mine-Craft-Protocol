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

tasks.check { dependsOn(verifyControl, verifyShowcase) }
