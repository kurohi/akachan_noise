plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// AGP 9 built-in Kotlin defaults to an older KGP; pin it to our Kotlin version.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint().editorConfigOverride(
            mapOf(
                // Compose composables are PascalCase by convention.
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
    }
    format("kts") {
        target("**/*.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    format("misc") {
        target("**/*.md", ".gitignore")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
