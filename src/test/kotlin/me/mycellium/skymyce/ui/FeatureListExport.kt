package me.mycellium.skymyce.ui

import me.mycellium.skymyce.commands.commandHelp
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the player-facing feature list (the menu catalog: category → section → feature) as Markdown, one `# Category`
 * block per category, for the Discord #features channel. Run with `./gradlew featureList`; the output is
 * build/feature-list.md and relay/scripts/discord-posts.mjs posts it.
 */
internal fun featureListMarkdown(): String = buildString {
    MenuCategory.entries.forEach { category ->
        val features = MenuCatalog.definitions.filter { it.category == category && it.searchable }
        if (features.isEmpty()) return@forEach
        append("# ${category.title}\n${category.description}\n")
        features.groupBy { it.subcategory }.forEach { (section, items) ->
            append("\n**$section**\n")
            items.forEach { append("- **${it.title}**: ${it.description}\n") }
        }
        append('\n')
    }
    append("# Commands\nEverything is in the /sm menu; these are optional shortcuts.\n")
    commandHelp.filter { it.everyday }.forEach { append("- `${it.command}`: ${it.description}\n") }
}

fun main(args: Array<String>) {
    val text = featureListMarkdown()
    check(text.lines().count { it.startsWith("- **") } >= 20) { "Feature list is suspiciously short" }
    val output = Path.of(args.firstOrNull() ?: "build/feature-list.md")
    Files.createDirectories(output.toAbsolutePath().parent)
    Files.writeString(output, text)
    println("Feature list written to $output (${text.length} characters)")
}
