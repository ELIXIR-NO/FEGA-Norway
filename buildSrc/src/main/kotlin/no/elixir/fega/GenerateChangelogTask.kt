package no.elixir.fega

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

abstract class GenerateChangelogTask : DefaultTask() {
    @get:Input
    @get:Option(option = "sinceTag", description = "Generate changelog since specified tag (default: latest tag for component)")
    @get:Optional
    abstract val sinceTag: Property<String>

    @get:Input
    @get:Option(option = "toTag", description = "Generate changelog to specified tag (default: HEAD)")
    @get:Optional
    abstract val toTag: Property<String>

    @get:Input
    @get:Option(option = "fallbackCommitCount", description = "Number of commits to include when falling back to recent commits")
    @get:Optional
    abstract val fallbackCommitCount: Property<Int>

    @get:Input
    @get:Optional
    abstract val outputDir: Property<String>

    init {
        sinceTag.convention("")
        toTag.convention("HEAD")
        fallbackCommitCount.convention(20)
        outputDir.convention("")
        description = "Generates a CHANGELOG.md file based on git commits for the component"
        group = "documentation"
    }

    @TaskAction
    fun generateChangelog() {
        val projectDir = project.projectDir.absolutePath
        val componentName = project.name
        val componentPath = project.path.replace(":", "/")
            .removePrefix("/")

        val fullComponentPath = if (componentPath.isNotEmpty()) {
            componentPath
        } else {
            // Root project case
            ""
        }

        logger.lifecycle("Generating changelog for component: $componentName")
        logger.lifecycle("Component path: $fullComponentPath")

        val targetFile = if (outputDir.get().isNotEmpty()) {
            File(outputDir.get(), "${project.name}.md")
        } else {
            File(project.projectDir, "CHANGELOG.md")
        }
        
        val since = if (sinceTag.get().isEmpty()) {
            // Find the latest tag for this component
            val latestTagProcess = ProcessBuilder(
                "bash", "-c", "git describe --tags --abbrev=0 --match=\"$componentName-*\" 2>/dev/null || echo ''"
            ).directory(project.rootDir).start()
            
            latestTagProcess.waitFor()
            val latestTag = latestTagProcess.inputStream.bufferedReader().readText().trim()
            
            if (latestTag.isEmpty()) {
                // No tag found, use start of git history
                ""
            } else {
                latestTag
            }
        } else {
            sinceTag.get()
        }

        val sinceOption = if (since.isEmpty()) "" else "$since.."
        val toOption = toTag.get()
        val rangeOption = "$sinceOption$toOption"

        logger.lifecycle("Generating changelog from $rangeOption")

        // Get git log for the component directory
        val pathOption = if (fullComponentPath.isEmpty()) {
            ""
        } else {
            "-- $fullComponentPath"
        }

        val gitLogCmd = "git log --pretty=format:'%ad | %s | [%h](https://github.com/ELIXIR-NO/FEGA-Norway/commit/%H)' --date=short $rangeOption $pathOption"
        
        logger.lifecycle("Executing: $gitLogCmd")
        
        val process = ProcessBuilder(
            "bash", "-c", gitLogCmd
        ).directory(project.rootDir).start()
        
        process.waitFor()
        
        var gitLog = process.inputStream.bufferedReader().readText()
        val errorLog = process.errorStream.bufferedReader().readText()
        
        if (errorLog.isNotEmpty()) {
            logger.error("Error generating changelog: $errorLog")
        }

        // If no commits found with the range, fall back to the most recent 20 commits for the component
        if (gitLog.isBlank()) {
            logger.lifecycle("No commits found in specified range. Falling back to recent commits.")
            
            val fallbackCmd = "git log -n ${fallbackCommitCount.get()} --pretty=format:'%ad | %s | [%h](https://github.com/ELIXIR-NO/FEGA-Norway/commit/%H)' --date=short $pathOption"
            logger.lifecycle("Executing fallback: $fallbackCmd")
            
            val fallbackProcess = ProcessBuilder(
                "bash", "-c", fallbackCmd
            ).directory(project.rootDir).start()
            
            fallbackProcess.waitFor()
            gitLog = fallbackProcess.inputStream.bufferedReader().readText()
        }

        // Group commits by date
        val commitsByDate = gitLog.split("\n")
            .filter { it.isNotEmpty() }
            .groupBy { it.substringBefore(" | ") }
            .toSortedMap(Comparator.reverseOrder())

        // Generate changelog content
        val title = "# Changelog for $componentName"
        val description = "\nAll notable changes to this component will be documented in this file.\n"
        
        val currentVersion = getCurrentVersion(project, componentName)
        val versionHeader = if (currentVersion.isNotEmpty()) {
            "\n## Version $currentVersion (${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)})\n"
        } else {
            "\n## Unreleased\n"
        }

        val content = StringBuilder().apply {
            append(title)
            append(description)
            append(versionHeader)
            
            if (commitsByDate.isEmpty()) {
                append("\nNo changes recorded.\n")
            } else {
                commitsByDate.forEach { (date, commits) ->
                    append("\n### $date\n")
                    commits.forEach { commit ->
                        val parts = commit.split(" | ", limit = 3)
                        if (parts.size >= 3) {
                            val (_, message, hash) = parts
                            append("- $message ($hash)\n")
                        }
                    }
                }
            }
        }

        // Write to CHANGELOG.md
        targetFile.writeText(content.toString())
        logger.lifecycle("Changelog generated at ${targetFile.absolutePath}")
    }

    private fun getCurrentVersion(project: Project, componentName: String): String {
        // First check if version is set as project property
        if (project.hasProperty("version") && project.property("version") != "unspecified") {
            return project.property("version").toString()
        }

        // Try to get from latest git tag
        val latestTagProcess = ProcessBuilder(
            "bash", "-c", "git describe --tags --abbrev=0 --match=\"$componentName-*\" 2>/dev/null || echo ''"
        ).directory(project.rootDir).start()
        
        latestTagProcess.waitFor()
        val latestTag = latestTagProcess.inputStream.bufferedReader().readText().trim()
        
        if (latestTag.isNotEmpty()) {
            return latestTag.substringAfter("$componentName-")
        }

        return ""
    }
} 