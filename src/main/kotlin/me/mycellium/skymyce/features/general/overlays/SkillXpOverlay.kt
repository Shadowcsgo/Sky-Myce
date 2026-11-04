package me.mycellium.skymyce.features.general.overlays

import io.wispforest.owo.ui.base.BaseUIComponent
import io.wispforest.owo.ui.component.UIComponents
import io.wispforest.owo.ui.container.UIContainers
import io.wispforest.owo.ui.core.OwoUIGraphics
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.VerticalAlignment
import me.mycellium.skymyce.SkyMyceModule
import me.mycellium.skymyce.config.misc.GeneralConfig
import me.mycellium.skymyce.config.misc.StatusBarsConfig
import me.mycellium.skymyce.features.general.overlays.bars.BarMotion
import me.mycellium.skymyce.features.general.overlays.bars.StatusBarRenderer
import me.mycellium.skymyce.hud.HudTheme
import me.mycellium.skymyce.hud.hudLabel
import me.mycellium.skymyce.hud.hudPanel
import me.mycellium.skymyce.hud.updateTextLines
import me.mycellium.skymyce.utils.NumberUtils
import me.mycellium.skymyce.hud.updateText
import me.mycellium.skymyce.hud.widget.Anchor
import me.mycellium.skymyce.hud.widget.Widget
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.hypixel.SkillExpGainedEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.InventoryChangeEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.getRawLore
import tech.thatgravyboat.skyblockapi.api.events.info.ActionBarWidget
import tech.thatgravyboat.skyblockapi.api.events.info.ActionBarWidgetChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.info.RenderActionBarWidgetEvent
import tech.thatgravyboat.skyblockapi.api.events.info.SkillXpLiteralActionBarWidgetChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.info.SkillXpPercentActionBarWidgetChangeEvent
import tech.thatgravyboat.skyblockapi.api.remote.hypixel.HypixelSkillAPI.Skill
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Progress toward the next skill level from an action bar reading: a percentage (0–100) or current/needed XP.
 * Null when neither gives a usable share (a maxed skill shows only its total).
 */
internal fun skillProgress(percent: Float?, current: Long?, needed: Long?): Double? = when {
    current != null && needed != null && needed > 0 -> (current.toDouble() / needed).coerceIn(0.0, 1.0)
    percent != null && percent.isFinite() && percent >= 0 -> (percent / 100.0).coerceIn(0.0, 1.0)
    else -> null
}

/** Gains over the last [windowMs], scaled to an hour; the rate counts from the first gain still in the window, at least a minute. */
internal class HourlyRate(private val windowMs: Long = 600_000) {
    private val gains = ArrayDeque<Pair<Long, Double>>()

    fun add(amount: Double, now: Long) { gains.addLast(now to amount) }

    /** Null once nothing has been gained for the whole window, so idle skills drop off the widget. */
    fun perHour(now: Long): Double? {
        while (gains.isNotEmpty() && now - gains.first().first > windowMs) gains.removeFirst()
        val first = gains.firstOrNull() ?: return null
        return gains.sumOf { it.second } / (now - first.first).coerceAtLeast(60_000) * 3_600_000
    }
}

/** The amount from a reward line such as "+400 HOTM Experience", in chat or item lore. */
internal fun hotmXpReward(line: String) = Regex("\\+([\\d,]+) HOTM Experience").matchEntire(line.trim())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

object SkillXpOverlay : SkyMyceModule() {
    private val skillRates = HashMap<Skill, HourlyRate>()
    private val hotmRate = HourlyRate()
    private var commissionScreen: Any? = null
    private val claimable = HashMap<Int, Double>()
    private val ratesContent by lazy { hudPanel(decorated = false) }

    val ratesWidget = Widget("xp_per_hour", "XP Per Hour", 10, 120, 1f) {
        if (!GeneralConfig.xpPerHour) return@Widget null
        val now = System.currentTimeMillis()
        val lines = buildList {
            hotmRate.perHour(now)?.let { add("§7HOTM XP/h: §b${NumberUtils.condense(it)}") }
            skillRates.entries.sortedBy { it.key.name }.forEach { (skill, rate) ->
                rate.perHour(now)?.let { add("§7${skill.name.lowercase().replaceFirstChar(Char::uppercase)} XP/h: §b${NumberUtils.condense(it)}") }
            }
        }
        if (lines.isEmpty()) null else ratesContent.updateTextLines(lines)
    }

    @Subscription
    fun onSkillXp(event: SkillExpGainedEvent) {
        if (event.amount > 0) skillRates.getOrPut(event.skill) { HourlyRate() }.add(event.amount.toDouble(), System.currentTimeMillis())
    }

    @Subscription
    fun onHotmChat(event: ChatReceivedEvent.Pre) {
        hotmXpReward(event.text)?.let { hotmRate.add(it, System.currentTimeMillis()) }
    }

    /** Claiming prints nothing in chat, so a completed commission whose slot stops being completed in the open menu counts as claimed. */
    @Subscription
    fun onCommissionSlot(event: InventoryChangeEvent) {
        if (event.title != "Commissions" || event.isInPlayerInventory) return
        if (event.screen !== commissionScreen) { commissionScreen = event.screen; claimable.clear() }
        val lore = event.item.getRawLore()
        val reward = if (lore.any { "COMPLETED" in it }) lore.firstNotNullOfOrNull(::hotmXpReward) else null
        if (reward != null) claimable[event.slot.index] = reward
        else claimable.remove(event.slot.index)?.let { hotmRate.add(it, System.currentTimeMillis()) }
    }

    private var display = ""
    private var skill: Skill? = null
    private var progress: Double? = null
    private var timeSince: Duration = 0.milliseconds
    private val icon by lazy { UIComponents.item(ItemStack(Items.BARRIER)) }
    private val label by lazy { hudLabel() }
    private val bar by lazy { ProgressBar() }
    private val text by lazy { UIContainers.verticalFlow(Sizing.content(), Sizing.content()).gap(2).apply { child(label) } }
    private val content by lazy {
        UIContainers.horizontalFlow(Sizing.content(), Sizing.content()).gap(5).apply {
            verticalAlignment(VerticalAlignment.CENTER)
            allowOverflow(true)
            children(listOf(icon, text))
        }
    }

    val widget = Widget("skill_xp_display", "Skill Xp Display", 480, 290, anchor = Anchor.CENTER) {
        if (!GeneralConfig.displayXp) return@Widget null

        val now = System.currentTimeMillis().milliseconds
        if (now - timeSince > 5.seconds) return@Widget null

        val item = skill?.skillItem() ?: Items.BARRIER
        if (icon.stack().item != item) icon.stack(ItemStack(item))
        label.updateText(display)
        val wantBar = GeneralConfig.displayXpBar && progress != null
        if (wantBar && bar.parent() == null) text.child(bar) else if (!wantBar && bar.parent() != null) text.removeChild(bar)
        bar.progress = progress ?: 0.0
        content
    }

    @Subscription
    fun onWidgetRender(event: RenderActionBarWidgetEvent) {
        if (!GeneralConfig.displayXp) return

        if (event.widget == ActionBarWidget.SKILL_XP || event.widget == ActionBarWidget.SKILL_XP_LITERAL) {
            event.cancel()
        }
    }

    @Subscription
    fun onWidgetEvent(event: ActionBarWidgetChangeEvent) {
        when (event) {
            is SkillXpPercentActionBarWidgetChangeEvent -> {
                display = event.new
                skill = event.skill
                progress = skillProgress(event.percent, null, null)
                timeSince = System.currentTimeMillis().milliseconds
            }

            is SkillXpLiteralActionBarWidgetChangeEvent -> {
                display = event.new
                skill = event.skill
                progress = skillProgress(null, event.current, event.needed)
                timeSince = System.currentTimeMillis().milliseconds
            }
        }
    }

    /** A slim bar under the label, as wide as the label (at least 80), easing to new progress in the HUD accent. */
    private class ProgressBar : BaseUIComponent() {
        var progress = 0.0
        private val motion = BarMotion()

        init { sizing(Sizing.fixed(80), Sizing.fixed(4)) }

        override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val w = maxOf(80, label.width())
            if (width() != w) horizontalSizing(Sizing.fixed(w))
            val now = System.currentTimeMillis()
            motion.update(progress, progress * 100, true, now, StatusBarsConfig.animate)
            val accent = HudTheme.ACCENT and 0xFFFFFF
            val left = x(); val top = y()
            graphics.fill(left, top, left + width(), top + 4, StatusBarRenderer.argb(0x000000, 0.55))
            val end = left + 1 + ((width() - 2) * motion.shown).toInt()
            if (end > left + 1) {
                graphics.fill(left + 1, top + 1, end, top + 3, StatusBarRenderer.argb(accent, 1.0))
                graphics.fill(left + 1, top + 1, end, top + 2, StatusBarRenderer.argb(StatusBarRenderer.light(accent, 0.45), 1.0))
            }
        }
    }

    fun Skill.skillItem(): Item = when(this) {
        Skill.COMBAT -> Items.STONE_SWORD
        Skill.FARMING -> Items.GOLDEN_HOE
        Skill.FISHING -> Items.FISHING_ROD
        Skill.MINING -> Items.STONE_PICKAXE
        Skill.FORAGING -> Items.JUNGLE_SAPLING
        Skill.ENCHANTING -> Items.ENCHANTING_TABLE
        Skill.ALCHEMY -> Items.BREWING_STAND
        Skill.CARPENTRY -> Items.CRAFTING_TABLE
        Skill.RUNECRAFTING -> Items.MAGMA_CREAM
        Skill.TAMING -> Items.POLAR_BEAR_SPAWN_EGG
        Skill.SOCIAL -> Items.EMERALD
        Skill.HUNTING -> Items.LEAD
    } ?: Items.BARRIER
}
