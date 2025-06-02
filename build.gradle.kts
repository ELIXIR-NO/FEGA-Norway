plugins {
    id("java")
    id("formatting-conventions")
}

group = "no.elixir"

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

tasks.wrapper {
    gradleVersion = "8.10"
}

// Apply the changelog plugin to all projects
allprojects {
    apply(plugin = "no.elixir.fega.changelog")
}

// Add a task to generate changelogs for all components
tasks.register("generateAllChangelogs") {
    group = "documentation"
    description = "Generates CHANGELOG.md files for all components"
    
    // Make the task depend on the generateChangelog tasks of all subprojects
    dependsOn(subprojects.map { it.tasks.named("generateChangelog") })
}
