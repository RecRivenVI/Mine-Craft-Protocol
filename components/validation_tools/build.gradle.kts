val verifyInstanceProfiles by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/Test-InstanceProfiles.ps1",
        )
    }

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

val verifyHookCompatibilityTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/compatibility-standard_gui/task/Test-Phase7HookCompatibilityGate.ps1",
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

val verifyAlphaConfiguration by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--test",
            "components/validation_tools/regression-v1_release/task/alpha-configuration.test.mjs",
            "components/validation_tools/regression-v1_release/task/alpha-consumer.test.mjs",
        )
    }

val verifyContinuousPerformanceTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--test",
            "components/validation_tools/conformance-world_read/task/continuous-performance-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/continuous-functional-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/input-context-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/region-stream-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-placement-receipts-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/interaction-baseline-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/mcp-world-baseline.test.mjs",
            "components/validation_tools/conformance-world_read/task/block-entity-pages-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-items-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-state-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-entities-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-environment-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-relations-actions.test.mjs",
            "components/validation_tools/conformance-world_read/task/world-debug-loading-actions.test.mjs",
        )
    }

val verifyAlphaSnapshots by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/regression-v1_release/task/Test-AlphaSnapshot.ps1",
        )
    }

val verifyInstalledEvidence by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--test",
            "components/validation_tools/integration-packaged_runtime/task/installed-evidence.test.mjs",
        )
    }

val verifyInstalledCleanup by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "pwsh",
            "-NoProfile",
            "-File",
            "components/validation_tools/integration-packaged_runtime/task/Test-InstalledInstanceCleanup.ps1",
        )
    }

val verifyServerConsoleTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--check",
            "components/validation_tools/integration-server_peer/task/server-console-smoke.mjs",
        )
    }

val verifyServerWorldDebugTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--check",
            "components/validation_tools/integration-server_peer/task/server-world-debug-checks.mjs",
        )
    }

val verifyOptionalInstallationTools by
    tasks.registering(Exec::class) {
        workingDir(rootDir.parentFile.parentFile)
        commandLine(
            "node",
            "--check",
            "components/validation_tools/integration-server_peer/task/optional-install-smoke.mjs",
        )
    }

tasks.check {
    dependsOn(
        verifyInstanceProfiles,
        verifyServerConsoleTools,
        verifyServerWorldDebugTools,
        verifyOptionalInstallationTools,
        verifyAlphaSnapshots,
        verifyInstalledEvidence,
        verifyInstalledCleanup,
        verifyControl,
        verifyHookCompatibilityTools,
        verifyShowcase,
        verifyVanillaUiTools,
        verifyWorldReadTools,
        verifyAlphaConfiguration,
        verifyContinuousPerformanceTools,
    )
}
