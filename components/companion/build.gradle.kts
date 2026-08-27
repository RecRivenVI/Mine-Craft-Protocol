val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val buildCompanion by
    tasks.registering(Exec::class) {
        commandLine(npmCommand, "run", "build")
    }
val testCompanion by
    tasks.registering(Exec::class) {
        commandLine(npmCommand, "test")
    }

tasks.assemble { dependsOn(buildCompanion) }

tasks.check { dependsOn(testCompanion) }
