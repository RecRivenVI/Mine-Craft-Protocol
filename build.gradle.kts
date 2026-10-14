import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import io.github.recrivenvi.conventions.component.ComponentConventions

// One pruned walk replaces Spotless's union of one walk per glob. Keep the template's file scope.
extensions.configure<SpotlessExtension> {
    setLineEndings(LineEnding.UNIX)
    format("misc") {
        target(
            fileTree(rootDir) {
                include(
                    "*.md",
                    "*.properties",
                    "*.toml",
                    "LICENSE",
                    "LICENSE.txt",
                    "NOTICE",
                    "COPYING",
                    "COPYING.LESSER",
                    ".gitmodules",
                    "licenses/**",
                    ".gitattributes",
                    ".editorconfig",
                    ".gitignore",
                    "gradle/*.toml",
                    ".github/**/*.yml",
                    ".github/**/*.yaml",
                    ".github/**/*.md",
                    "documents/**/*.md",
                )
                ComponentConventions.TEXT_EXTENSIONS.forEach { include("validations/**/*.$it") }
                exclude("local.toml", "validations/*/instance/**", "validations/*/result/**")
            },
        )
        targetExclude()
    }
}
