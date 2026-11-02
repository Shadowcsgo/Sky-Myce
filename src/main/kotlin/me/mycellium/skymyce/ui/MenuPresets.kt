package me.mycellium.skymyce.ui

import com.teamresourceful.resourcefulconfig.api.types.options.EntryType
import me.mycellium.skymyce.config.SettingRow

/**
 * One click sets several related settings. Values go through the same config entries the settings page edits, so a
 * preset never adds hidden state: afterwards every value is visible (and individually changeable) in its own page.
 */
internal data class MenuPreset(val id: String, val title: String, val description: String, val featureId: String,
                               val values: Map<String, Any>, val keywords: String = "")

private const val GRAPHICS = "Theme & Appearance/graphicsQuality"
private const val MOTION = "Theme & Appearance/motion"
private const val GIFS = "General/Global SkyMyce Chat/animateGifs"
private const val PREVIEWS = "General/Global SkyMyce Chat/mediaPreviews"

internal val menuPresets = listOf(
    MenuPreset("performance", "Best performance", "Extremely Light graphics, reduced animations, no GIF animation or chat thumbnails. For weak PCs and laptops.",
        "settings-graphics", mapOf(GRAPHICS to GuiQuality.EXTREMELY_LIGHT, MOTION to MotionPreference.REDUCED, GIFS to false, PREVIEWS to false),
        "fps lag low end laptop potato fast light"),
    MenuPreset("balanced", "Balanced (default)", "Auto graphics, animations that follow it, and GIFs and chat thumbnails on.",
        "settings-graphics", mapOf(GRAPHICS to GuiQuality.AUTO, MOTION to MotionPreference.MATCH, GIFS to true, PREVIEWS to true),
        "default normal recommended auto"),
    MenuPreset("looks", "Best looks", "High graphics and expressive animations, with GIFs and chat thumbnails. For strong PCs.",
        "settings-graphics", mapOf(GRAPHICS to GuiQuality.HIGH, MOTION to MotionPreference.EXPRESSIVE, GIFS to true, PREVIEWS to true),
        "pretty high quality smooth beautiful"),
    MenuPreset("npc-box", "NPC lines in the box only", "Show NPC dialogue in the box and remove those lines and the option prompt from chat.",
        "npc-dialogue", mapOf("General/NPC Dialogue/enabled" to true, "General/NPC Dialogue/hideChat" to true, "General/NPC Dialogue/hideOptions" to true),
        "dialogue chat clean quest"),
    MenuPreset("npc-chat", "NPC lines in chat", "Turn the dialogue box off so NPC lines stay in chat as usual.",
        "npc-dialogue", mapOf("General/NPC Dialogue/enabled" to false, "General/NPC Dialogue/hideChat" to false, "General/NPC Dialogue/hideOptions" to false),
        "dialogue vanilla normal"),
)

/** True when every value the preset sets is already in place. */
internal fun menuPresetActive(preset: MenuPreset, setting: (String) -> SettingRow?): Boolean =
    preset.values.all { (path, value) -> setting(path)?.entry?.get() == value }

/** Writes the preset's values; false (and nothing written) if a path is missing or a value has the wrong type. */
internal fun applyMenuPreset(preset: MenuPreset, setting: (String) -> SettingRow?): Boolean {
    val targets = preset.values.map { (path, value) -> (setting(path) ?: return false) to value }
    if (targets.any { (row, value) -> !menuPresetFits(row, value) }) return false
    targets.forEach { (row, value) -> when (value) { is Boolean -> row.entry.setBoolean(value); is Enum<*> -> row.entry.setEnum(value) } }
    return true
}

private fun menuPresetFits(row: SettingRow, value: Any) = when (value) {
    is Boolean -> row.entry.type() == EntryType.BOOLEAN
    is Enum<*> -> row.entry.type() == EntryType.ENUM && row.entry.objectType() == value.declaringJavaClass
    else -> false
}

/** Presets whose title, description or keywords contain every word of [query]. */
internal fun menuPresetMatches(query: String): List<MenuPreset> {
    val words = query.lowercase().split(' ').filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    return menuPresets.filter { preset -> "${preset.title} ${preset.description} ${preset.keywords} preset".lowercase().let { text -> words.all(text::contains) } }
}
