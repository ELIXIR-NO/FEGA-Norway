plugins {
    id("com.diffplug.spotless")
}

spotless {
    format("misc") {
        target("**/*.gradle", ".gitattributes", ".gitignore")

        // define the steps to apply to those files
        trimTrailingWhitespace()
        leadingSpacesToTabs()
        endWithNewline()
    }
    java {
        target("src/**/*.java")
        importOrder()
        removeUnusedImports()
        cleanthat()
        googleJavaFormat()
        formatAnnotations()
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        diktat()
    }
    kotlinGradle { ktlint() }
}