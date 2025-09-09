import java.text.SimpleDateFormat
import java.util.*

val repoUrl = "https://github.com/ELIXIR-NO/FEGA-Norway"

tasks.register("generateChangelog") {
    group = "changelog"
    doLast {
        // 1. Get module name from project directory
        val moduleName = project.name

        // 2. Get latest tag for this module
        val tagPattern = "$moduleName-*"
        val tagProcess = ProcessBuilder(
            "bash", "-c", "git tag --list '$tagPattern' --sort=-creatordate | head -n 1"
        )
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
        val latestTag = tagProcess.inputStream.bufferedReader().readText().trim()
        tagProcess.waitFor()

        // 3. Get commits since the latest tag (or all if no tag)
        val logRange = if (latestTag.isNotEmpty()) "$latestTag..HEAD" else ""
        val logCommand = if (logRange.isNotEmpty())
            listOf("git", "log", "--pretty=format:%h|%s", logRange, "--", project.projectDir.toString())
        else
            listOf("git", "log", "--pretty=format:%h|%s", "--", project.projectDir.toString())

        val process = ProcessBuilder(logCommand)
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        // Emojis for each group
        val groupEmojis = mapOf(
            "Features" to "🚀",
            "Bug Fixes" to "🐛",
            "Chores" to "🧹",
            "Documentation" to "📚",
            "Other" to "🔧"
        )

        // Group commits
        val commitGroups = mutableMapOf(
            "Features" to mutableListOf(),
            "Bug Fixes" to mutableListOf(),
            "Chores" to mutableListOf(),
            "Documentation" to mutableListOf(),
            "Other" to mutableListOf<String>()
        )

        // Helper to link PRs/issues
        fun linkRefs(message: String): String {
            val prLinked = message.replace(
                Regex("""\(#(\d+)\)"""),
                "([#$1]($repoUrl/pull/$1))"
            )
            return prLinked.replace(
                Regex("""(?<!\()#(\d+)\b"""),
                "([#$1]($repoUrl/issues/$1))"
            )
        }

        output.lines().forEach { line ->
            if (line.isNotBlank()) {
                val (hash, message) = line.split("|", limit = 2)
                val linkedHash = "[`${hash}`]($repoUrl/commit/$hash)"
                val linkedMessage = linkRefs(message)
                when {
                    message.startsWith("feat") -> commitGroups["Features"]?.add("$linkedHash $linkedMessage")
                    message.startsWith("fix") -> commitGroups["Bug Fixes"]?.add("$linkedHash $linkedMessage")
                    message.startsWith("chore") -> commitGroups["Chores"]?.add("$linkedHash $linkedMessage")
                    message.startsWith("docs") -> commitGroups["Documentation"]?.add("$linkedHash $linkedMessage")
                    else -> commitGroups["Other"]?.add("$linkedHash $linkedMessage")
                }
            }
        }

        // Get current date
        val date = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val version = project.findProperty("version")?.toString()
            ?: latestTag.ifEmpty { "Unreleased" }

        // Get PR title if provided
        val prTitle = project.findProperty("prTitle")?.toString()

        val changelogFile = file("CHANGELOG.md")
        changelogFile.writeText("# Changelog\n\n")
        changelogFile.appendText("## [$version] - $date\n\n")

        // Add PR title at the top if present
        if (!prTitle.isNullOrBlank()) {
            changelogFile.appendText("> **$prTitle**\n\n")
        }

        commitGroups.forEach { (group, commits) ->
            if (commits.isNotEmpty()) {
                val emoji = groupEmojis[group] ?: ""
                changelogFile.appendText("### $emoji $group\n")
                commits.forEach { commit ->
                    changelogFile.appendText("- $commit\n")
                }
                changelogFile.appendText("\n")
            }
        }

        println("✨ Fancy changelog generated at ${changelogFile.absolutePath}")
    }
}