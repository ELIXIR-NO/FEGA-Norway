package no.elixir.fega

import org.gradle.api.Plugin
import org.gradle.api.Project

class ChangelogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register the task
        project.tasks.register("generateChangelog", GenerateChangelogTask::class.java)
    }
} 