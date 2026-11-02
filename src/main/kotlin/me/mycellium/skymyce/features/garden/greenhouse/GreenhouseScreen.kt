package me.mycellium.skymyce.features.garden.greenhouse

import io.wispforest.owo.ui.base.BaseUIComponent
import io.wispforest.owo.ui.component.ButtonComponent
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.ScrollContainer
import io.wispforest.owo.ui.container.UIContainers
import io.wispforest.owo.ui.core.*
import me.mycellium.skymyce.config.misc.GreenhouseConfig
import me.mycellium.skymyce.config.SettingsScreen
import me.mycellium.skymyce.hud.themed
import me.mycellium.skymyce.ui.*
import me.mycellium.skymyce.utils.MC
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component

internal enum class GreenhouseTab(val title: String) {
    OVERVIEW("Overview"), MUTATIONS("Mutations"), SHOPPING("Shopping"), UNLOCK("Unlock guide"), HISTORY("History")
}

internal enum class MutationSort(val title: String) { PROFIT("Profit/h"), NAME("Name"), CYCLE("Growth time") }

/** Mutation browser rows: search by name or ingredient, then sort. Pure so it can be checked without a client. */
internal fun greenhouseMutationRows(rows: List<GreenhousePlanner.PlotRanked>, catalog: PlantCatalog, query: String, sort: MutationSort): List<GreenhousePlanner.PlotRanked> {
    val words = query.lowercase().split(' ').filter(String::isNotBlank)
    val matching = rows.filter { row ->
        val text = (listOf(row.entry.plant.name) + row.entry.plant.requires.keys.map { catalog[it]?.name ?: it }).joinToString(" ").lowercase()
        words.all(text::contains)
    }
    return when (sort) {
        MutationSort.PROFIT -> matching.sortedByDescending { it.plotPerHour ?: it.entry.profitPerHour }
        MutationSort.NAME -> matching.sortedBy { it.entry.plant.name }
        MutationSort.CYCLE -> matching.sortedBy { it.entry.cycleHours }
    }
}

/**
 * The Greenhouse workspace: plots (layout, scan, growth, profit), a mutation browser, the shopping list, the unlock guide
 * and harvest history. Every action calls the same planner functions as `/sm greenhouse`.
 */
class GreenhouseScreen internal constructor(private var tab: GreenhouseTab) : MenuScreen(Component.literal("Greenhouses"), MenuPage.GREENHOUSE) {
    constructor() : this(GreenhouseTab.OVERVIEW)
    private lateinit var results: ScrollContainer<FlowLayout>
    private lateinit var controls: FlowLayout
    private var rendered: List<Any?> = emptyList()
    private var query = ""
    private var sort = MutationSort.PROFIT
    private var expanded: String? = null
    private var rankingPlot: String? = null
    private val panelWidth get() = menuWidth
    private val compact get() = panelWidth < 600

    override fun buildContent(root: FlowLayout) {
        root.surface(Surface.BLANK)
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER)
        root.child(UIContainers.verticalFlow(Sizing.fixed(panelWidth), Sizing.fixed(menuHeight)).apply {
            surface(MenuTheme.panel()); padding(Insets.of(12)); gap(8)
            child(row().apply {
                gap(8)
                child(UIContainers.verticalFlow(Sizing.fixed(3), Sizing.fixed(18)).surface(MenuTheme.tintedPanel { MenuTheme.ACCENT }))
                child(label("§lGreenhouses").horizontalSizing(Sizing.expand()))
                child(button(remindersLabel(), 92) { it.message = Component.literal(remindersLabel(GreenhousePlanner.toggleReminders())) }
                    .tooltip(Component.literal("Greenhouse reminders: shown in game while you play, DMed on Discord otherwise")))
                // Opened with this screen as its parent, so Done and Esc come back here.
                child(button("Settings", 64) { navigateMenu(SettingsScreen.forFeature(this@GreenhouseScreen, "greenhouse-planner")) })
            })
            child(row().apply {
                gap(4)
                GreenhouseTab.entries.forEach { option ->
                    child(button(option.title, if (compact) 70 else 92) { if (tab != option) { tab = option; buildControls(); update() } }
                        .apply { navigation = true; centeredNavigation = true; selected = { tab == option } })
                }
            })
            controls = row().apply { gap(4) }
            child(controls)
            results = menuScroll(Sizing.fill(), Sizing.expand(), UIContainers.verticalFlow(Sizing.fill(), Sizing.content())).apply {
                scrollbar(ScrollContainer.Scrollbar.flat(Color.ofRgb(MenuTheme.ACCENT))); scrollbarThiccness(2); padding(Insets.right(4))
            }
            child(results)
        })
        buildControls()
        update()
    }

    /** Tab-specific controls sit outside the results, so typing a search never rebuilds the field. */
    private fun buildControls() {
        controls.clearChildren()
        if (tab != GreenhouseTab.MUTATIONS) return
        controls.child(MenuTextBox(Sizing.expand()).apply {
            setMaxLength(40); text(query); setHint(Component.literal("Search mutation or ingredient..."))
            onChanged().subscribe { query = it; update() }
        })
        controls.child(button("Sort: ${sort.title}", 110) {
            sort = MutationSort.entries[(sort.ordinal + 1) % MutationSort.entries.size]
            it.message = Component.literal("Sort: ${sort.title}"); update()
        }.tooltip(Component.literal("Profit per hour (estimated), name or growth time")))
        val plots = GreenhousePlanner.plots
        if (plots.size > 1) controls.child(button("Plot: ${rankingPlot().name}", 90) {
            rankingPlot = plots[(plots.indexOf(rankingPlot()) + 1) % plots.size].name
            it.message = Component.literal("Plot: ${rankingPlot().name}"); update()
        }.tooltip(Component.literal("Rank and plan for this plot (its locked cells and upgrades)")))
    }

    private fun rankingPlot() = GreenhousePlanner.plots.firstOrNull { it.name == rankingPlot } ?: GreenhousePlanner.selected()

    override fun tick() {
        super.tick()
        if (menuClosing || !::results.isInitialized) return
        // Timers read in minutes, so a once-a-minute refresh keeps them current without rebuilding every tick.
        val state = listOf(GreenhousePlanner.revision, System.currentTimeMillis() / 60_000)
        if (state != rendered) update()
    }

    private fun update() {
        rendered = listOf(GreenhousePlanner.revision, System.currentTimeMillis() / 60_000)
        val now = System.currentTimeMillis()
        val plots = GreenhousePlanner.plots.toList()
        results.child(UIContainers.verticalFlow(Sizing.fill(), Sizing.content()).apply {
            gap(6)
            when (tab) {
                GreenhouseTab.OVERVIEW -> {
                    child(overview(plots, now))
                    if (plots.isEmpty()) child(note("No Greenhouse plots yet. In your Garden, look at a plot's corner soil and run /sm greenhouse anchor."))
                    plots.forEach { child(plotCard(it, now)) }
                }
                GreenhouseTab.MUTATIONS -> mutations(this)
                GreenhouseTab.SHOPPING -> shopping(this, plots, now)
                GreenhouseTab.UNLOCK -> unlock(this, now)
                GreenhouseTab.HISTORY -> history(this, now)
            }
        })
    }

    private fun note(text: String) = label(text, MUTED).horizontalSizing(Sizing.fill()).margins(Insets.of(8))

    /** Profit-ranked mutations for one plot; details show requirements and plan it there. */
    private fun mutations(list: FlowLayout) {
        val catalog = GreenhousePlanner.catalog ?: return run { list.child(note("Greenhouse data is unavailable.")) }
        val plot = rankingPlot()
        val rows = greenhouseMutationRows(GreenhousePlanner.plotRanking(plot), catalog, query, sort)
        list.child(label("Estimated profit for plot ${plot.name} at current Bazaar prices (skymutations.eu formula). Locked cells, upgrades and Garden level apply.", MUTED)
            .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
        if (rows.isEmpty()) list.child(note(if (query.isBlank()) "No mutation has Bazaar prices yet; prices load after a moment in SkyBlock." else "No mutation matches \"$query\"."))
        rows.forEach { row ->
            val plant = row.entry.plant
            val analysed = !plant.mutation || GreenhousePlanner.isAnalyzed(plant.id)
            list.child(card(if (analysed) MenuTheme.ACCENT else MUTED).apply {
                child(row().apply {
                    gap(6)
                    child(label("§l${plant.name}").horizontalSizing(Sizing.expand()))
                    if (!analysed) child(GreenhouseBadge("Not analysed", MUTED))
                    val perHour = row.plotPerHour ?: row.entry.profitPerHour
                    child(label("${GreenhousePlanner.signed(perHour)}/h est.", if (perHour >= 0) GREEN else MenuTheme.RED))
                    child(label("${"%.1f".format(row.entry.cycleHours)}h", MUTED).tooltip(Component.literal("Growth cycle (estimated)")))
                    row.count?.let { child(label("×$it", MUTED).tooltip(Component.literal("Targets that fit this plot (quick solve)"))) }
                    child(button(if (expanded == plant.id) "Hide" else "Details", 56) { expanded = if (expanded == plant.id) null else plant.id; update() })
                    child(button("Plan", 48) { GreenhousePlanner.planFor(plot, plant.id); showMenuToast("Planning ${plant.name} on plot ${plot.name}…", MenuToastKind.INFO) }
                        .tooltip(Component.literal("Solve a layout for ${plant.name} on plot ${plot.name}; it replaces the current plan")))
                })
                if (expanded == plant.id) {
                    child(label("Needs: " + plant.requires.entries.joinToString(", ") { (id, n) -> "$n× ${catalog[id]?.name ?: id}" }, TEXT)
                        .apply { horizontalSizing(Sizing.fill()) })
                    child(label("Soil: ${plant.soils.joinToString(", ") { GreenhousePlanner.soilName(it) }} · ${plant.stages} stages · " +
                        "revenue ${GreenhousePlanner.signed(row.entry.revenue)}, inputs ${GreenhousePlanner.signed(-row.entry.inputCost)} per cycle", MUTED)
                        .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
                    if (!analysed) child(label("Unlock it: grow it once, then analyse it with the Plant Diagnostics Tool. The Unlock guide tab tracks this.", MUTED)
                        .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
                }
            })
        }
    }

    /** Per plot, what is missing and where it comes from. Get sends the one /gfs or /bz command, as the chat list does. */
    private fun shopping(list: FlowLayout, plots: List<GreenhousePlanner.Plot>, now: Long) {
        list.child(label("Purchases stay manual: Get takes items from your sacks (/gfs) or opens the Bazaar with the amount ready.", MUTED)
            .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
        if (plots.isEmpty()) list.child(note("No plots yet."))
        plots.forEach { plot ->
            val items = GreenhousePlanner.shoppingList(plot)
            list.child(card(if (items.isEmpty()) GREEN else MenuTheme.YELLOW).apply {
                child(row().apply {
                    gap(6)
                    child(label("§lPlot ${plot.name}").horizontalSizing(Sizing.expand()))
                    child(label(when {
                        plot.layout == null -> "No layout yet"
                        plot.scannedAt == 0L -> "Not checked: stand near the plot"
                        else -> "Checked ${GreenhousePlanner.age(now - plot.scannedAt)} ago"
                    }, MUTED).apply { fontSize = 8f })
                })
                if (plot.layout != null && plot.scannedAt > 0 && items.isEmpty()) child(label("Everything is planted.", GREEN))
                items.groupBy { it.source }.forEach { (source, group) ->
                    child(label(source.title, MUTED).apply { fontSize = 9f })
                    group.forEach { item ->
                        child(row().apply {
                            gap(6)
                            child(label(if (item.from != null) "${item.count}× ${item.name} §7(${item.fetch} ${item.from})" else "${item.count}× ${item.name}")
                                .horizontalSizing(Sizing.expand()))
                            child(button("Get", 40) { chat { GreenhousePlanner.get(plot.name, item.key) } }.tooltip(Component.literal(
                                if (source == ShoppingSource.BAZAAR) "Open ${item.name} in the Bazaar; ${item.count} goes into the amount sign"
                                else "Take ${item.fetch} ${item.from ?: item.name} from your sacks (/gfs)")))
                        })
                    }
                }
            })
        }
    }

    /** Which mutations are analysed, how fresh that is, and the manual steps the game still needs. */
    private fun unlock(list: FlowLayout, now: Long) {
        val catalog = GreenhousePlanner.catalog ?: return run { list.child(note("Greenhouse data is unavailable.")) }
        val mutations = catalog.plants.filter { it.mutation }
        val done = mutations.count { GreenhousePlanner.isAnalyzed(it.id) }
        list.child(card(MenuTheme.ACCENT).apply {
            child(row().apply {
                gap(6)
                child(label("§l$done of ${mutations.size} mutations analysed").horizontalSizing(Sizing.expand()))
                child(button("Start guide", 84) { chat(GreenhousePlanner::unlockGuide) }
                    .tooltip(Component.literal("Plans plots to grow what is still locked and walks you through it in chat")))
            })
            val read = GreenhousePlanner.mutationStatusReadAt
            child(label(if (read > 0) "Status read from the Carpenter's All Mutations menu ${GreenhousePlanner.age(now - read)} ago."
                else "Status not read yet: open the Carpenter's All Mutations menu once so analysed mutations are known.", if (read > 0) MUTED else MenuTheme.YELLOW)
                .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
            child(label("Manual steps: grow the mutation, then analyse it with the Plant Diagnostics Tool. The game confirms it in chat and the status updates here.", MUTED)
                .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
        })
        mutations.sortedWith(compareBy({ GreenhousePlanner.isAnalyzed(it.id) }, { it.name })).forEach { plant ->
            val analysed = GreenhousePlanner.isAnalyzed(plant.id)
            list.child(row().apply {
                gap(6); padding(Insets.horizontal(6))
                child(label(if (analysed) "§a✔ §f${plant.name}" else plant.name).horizontalSizing(Sizing.expand()))
                child(label(plant.requires.entries.joinToString(", ") { (id, n) -> "$n× ${catalog[id]?.name ?: id}" }.ifEmpty { plant.special ?: "" }, MUTED)
                    .apply { fontSize = 8f; horizontalSizing(Sizing.fill(45)) })
                if (!analysed) child(button("Mark analysed", 96) { GreenhousePlanner.markAnalyzed(plant.id) }
                    .tooltip(Component.literal("Only if the game already shows ${plant.name} as analysed")))
            })
        }
    }

    /** Recorded harvests only, valued at the price when harvested. */
    private fun history(list: FlowLayout, now: Long) {
        val harvests = GreenhousePlanner.history
        val catalog = GreenhousePlanner.catalog
        list.child(row().apply {
            gap(6); verticalAlignment(VerticalAlignment.TOP)
            child(harvested(harvestTotals(harvests, now), 49))
            child(UIContainers.verticalFlow(Sizing.fill(49), Sizing.content()).apply {
                gap(2); child(ProfitBars(dailyProfit(harvests, now, 14)))
                child(label("Last 14 days, estimated at the price when harvested", MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
            })
        })
        if (harvests.isEmpty()) return run { list.child(note("Harvests appear here once you harvest a mutation in a planned plot.")) }
        list.child(label("By mutation", MUTED).apply { fontSize = 9f })
        harvests.groupBy { it.plantId }.entries.sortedByDescending { (_, items) -> items.sumOf { it.revenue - it.cost } }.forEach { (id, items) ->
            list.child(row().apply {
                gap(6); padding(Insets.horizontal(6))
                child(label(catalog?.get(id)?.name ?: id).horizontalSizing(Sizing.expand()))
                child(label("${items.size} harvest${if (items.size == 1) "" else "s"}", MUTED))
                child(label(GreenhousePlanner.signed(items.sumOf { it.revenue - it.cost }), GREEN))
            })
        }
        list.child(label("Recent", MUTED).apply { fontSize = 9f })
        harvests.asReversed().take(30).forEach { harvest ->
            list.child(row().apply {
                gap(6); padding(Insets.horizontal(6))
                child(label("${GreenhousePlanner.age(now - harvest.at)} ago", MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.fixed(70)) })
                child(label("${catalog?.get(harvest.plantId)?.name ?: harvest.plantId} · plot ${harvest.plot}").horizontalSizing(Sizing.expand()))
                child(label(GreenhousePlanner.signed(harvest.revenue - harvest.cost), if (harvest.revenue >= harvest.cost) GREEN else MenuTheme.RED))
            })
        }
    }

    private fun overview(plots: List<GreenhousePlanner.Plot>, now: Long) = card(MenuTheme.ACCENT).apply {
        val perHour = plots.mapNotNull { it.layout }.sumOf(GreenhousePlanner::estimate)
        val daily = dailyProfit(GreenhousePlanner.history, now, 14)
        child(row().apply {
            child(label("§lAll plots").horizontalSizing(Sizing.expand()))
            child(label("${plots.size} plot${if (plots.size == 1) "" else "s"}", MUTED))
        })
        val history = UIContainers.verticalFlow(Sizing.expand(), Sizing.content()).apply {
            gap(2)
            child(ProfitBars(daily))
            child(label("Last 14 days ${GreenhousePlanner.signed(daily.sum())}, best day ${GreenhousePlanner.signed(daily.maxOrNull() ?: 0.0)}. Estimated at the price when harvested.", MUTED)
                .apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
        }
        child(row().apply {
            gap(6); verticalAlignment(VerticalAlignment.TOP)
            val share = if (compact) 49 else 24
            child(estimated(perHour, share)); child(harvested(harvestTotals(GreenhousePlanner.history, now), share))
            if (!compact) child(history)
        })
        if (compact) child(history)
    }

    private fun plotCard(plot: GreenhousePlanner.Plot, now: Long): FlowLayout {
        val layout = plot.layout
        val catalog = GreenhousePlanner.catalog
        val counts = plot.status.groupingBy { it }.eachCount()
        val growing = layout?.targets?.count { plot.status.getOrNull(it.row * GREENHOUSE_SIZE + it.col) == CellStatus.TARGET_GROWING.code } ?: 0
        val missing = plot.missingPlants.values.sum() + plot.missingSoil.values.sum()
        val stripe = when {
            layout == null -> MUTED
            plot.status.isEmpty() -> MUTED
            missing > 0 -> MenuTheme.YELLOW
            growing > 0 -> GREEN
            else -> MenuTheme.ACCENT
        }
        return card(stripe).apply {
            val target = layout?.targets?.firstOrNull()?.let { catalog?.get(it.plantId)?.name }
            val garden = GreenhousePlanner.gardenPlot(plot)
            val anchorText = plot.anchor?.let { "${garden?.let { n -> "Garden plot $n, a" } ?: "A"}nchored at ${it.pos.x} ${it.pos.y} ${it.pos.z}, rows run ${it.facing.name.lowercase()}" } ?: "Not anchored yet"
            // Teleporting sends /tptoplot, so it closes the menu like the other chat actions.
            val teleport = garden?.let { n -> button("Teleport", 64) { chat { GreenhousePlanner.teleport(plot) } }.tooltip(Component.literal("Go to Garden plot $n (/tptoplot $n)")) }
            child(row().apply {
                gap(6)
                child(label("§l${plot.name}").apply { fontSize = 12f })
                if (plot === GreenhousePlanner.selected()) child(GreenhouseBadge("Nearest", MenuTheme.ACCENT))
                child(label(anchorText, MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.expand()) })
                child(label(if (layout == null) "No plan" else layout.targets.groupingBy { it.plantId }.eachCount().entries
                    .joinToString(", ") { (id, n) -> "${catalog?.get(id)?.name ?: id} ×$n" }, if (layout == null) MUTED else MenuTheme.ACCENT).apply { fontSize = 12f })
            })
            if (layout == null) {
                child(row().apply {
                    gap(6)
                    child(label("Stand at the plot and run /sm greenhouse import to read what is planted, or plan one with auto or plan <mutation>.", MUTED).horizontalSizing(Sizing.expand()))
                    child(button("Import", 56) { chat { GreenhousePlanner.importPlot(plot, null) } }.tooltip(Component.literal("Read what is planted on this plot (stand near it)")))
                    child(button("Plan best", 70) { GreenhousePlanner.planFor(plot, null); showMenuToast("Planning plot ${plot.name}…", MenuToastKind.INFO) }
                        .tooltip(Component.literal("Solve a layout for the most profitable mutation; Mutations lists the others")))
                    teleport?.let(::child)
                    child(removeButton(plot))
                })
                return@apply
            }
            val used = plot.status.count { it != CellStatus.UNUSED.code }
            val built = counts[CellStatus.DONE.code].orZero() + counts[CellStatus.TARGET_WAITING.code].orZero() + counts[CellStatus.TARGET_GROWING.code].orZero()
            val percent = if (used == 0) 0 else built * 100 / used
            child(row().apply {
                gap(6)
                if (plot.status.isEmpty()) {
                    child(label("Not checked yet: visit the plot so the planner can see what is planted", MUTED).horizontalSizing(Sizing.expand()))
                } else {
                    child(label("$percent% built", if (missing > 0) MenuTheme.YELLOW else TEXT))
                    child(GreenhouseProgressBar(built.toLong(), used.toLong(), if (missing > 0) MenuTheme.YELLOW else GREEN).apply { horizontalSizing(Sizing.fill(if (compact) 25 else 45)) })
                    val detail = (if (missing == 0) "nothing missing" else "missing ${plot.missingPlants.values.sum()} crops, ${plot.missingSoil.values.sum()} soil") +
                        (counts[CellStatus.WRONG_CROP.code]?.let { ", $it wrong crops" } ?: "") + ", checked ${GreenhousePlanner.age(now - plot.scannedAt)} ago"
                    child(label(detail, if (missing > 0) MenuTheme.YELLOW else MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.expand()) })
                }
            })
            val rows = growthRows(plot, layout, now)
            val tiles = row().apply {
                gap(6); verticalAlignment(VerticalAlignment.TOP)
                child(growth(plot, layout, rows, now))
                child(estimated(GreenhousePlanner.estimate(layout), 32))
                child(harvested(harvestTotals(GreenhousePlanner.history, now, plot.name), 32))
            }
            if (compact) child(tiles)
            child(row().apply {
                gap(8); verticalAlignment(VerticalAlignment.TOP)
                child(GreenhouseGrid(layout, plot.status, if (panelWidth < 450) 12 else 16, plot.locked, { cell -> GreenhousePlanner.toggleLock(plot, cell) }) {
                    GreenhousePlanner.targetEstimate(plot, it, now)
                })
                child(UIContainers.verticalFlow(Sizing.expand(), Sizing.content()).apply {
                    gap(4)
                    if (!compact) child(tiles)
                    rows.filter { it.state != GrowthState.WAITING }.takeIf { it.isNotEmpty() }?.let { child(GrowthRows(it)) }
                    child(GreenhouseLegend(layout))
                    child(label("Faded = not planted, red edge = wrong soil, dot = growing but not analysed, fill = growth, yellow = fully grown, cross = locked. Hover a slot for details; click one to lock it out of the next plan.", MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.fill()) })
                    child(actions(listOfNotNull(
                        teleport,
                        button("Shopping list", 92) { chat { GreenhousePlanner.showMissing(plot) } }.tooltip(Component.literal("Everything still missing; click an item to fetch it from your sacks or buy it")),
                        button("Import", 56) { chat { GreenhousePlanner.importPlot(plot, null) } }
                            .tooltip(Component.literal("Replace this layout with what is planted now (stand near the plot)")),
                        button("Recompute", 76) { chat { GreenhousePlanner.plan(plot, force = true) } }
                            .tooltip(Component.literal("Solve a new layout for ${target ?: "the best mutation"}; the current one is replaced")),
                        removeButton(plot),
                    )))
                })
            })
        }
    }

    /** One row per target slot: analysed mutations first by time left, then growing but not analysed, then slots still waiting. */
    private fun growthRows(plot: GreenhousePlanner.Plot, layout: Layout, now: Long): List<GrowthRow> {
        val cycleMs = GreenhousePlanner.cycleHours(layout)?.let { (it * 3_600_000).toLong() }
        return layout.targets.map { placement ->
            val key = placement.row * GREENHOUSE_SIZE + placement.col
            val where = "R${placement.row + 1} C${placement.col + 1}"
            val code = plot.status.getOrNull(key)?.let(CellStatus::of)
            if (code != CellStatus.TARGET_GROWING) return@map GrowthRow(where, placement.plantId, GrowthState.WAITING, 0.0, 0, null)
            val estimate = GreenhousePlanner.targetEstimate(plot, placement, now)
                ?: return@map GrowthRow(where, placement.plantId, GrowthState.UNANALYSED, 0.0, 0, plot.spawned[key]?.let { at -> cycleMs?.let { at + it - now } })
            val state = when {
                estimate.ready -> GrowthState.READY
                estimate.paused -> GrowthState.PAUSED
                else -> GrowthState.GROWING
            }
            GrowthRow(where, placement.plantId, state, estimate.stage, estimate.stages, estimate.readyInMs, estimate.waitInMs)
        }.sortedWith(compareBy({ it.state.ordinal }, { it.readyInMs ?: Long.MAX_VALUE }))
    }

    /** When the next and the last mutation finish, from analysed readings or (estimated) spawn times. */
    private fun growth(plot: GreenhousePlanner.Plot, layout: Layout, rows: List<GrowthRow>, now: Long): FlowLayout {
        val cycle = GreenhousePlanner.cycleHours(layout) ?: return tile("Growing", "—", MUTED, 32, listOf("No target"))
        val growing = rows.count { it.state != GrowthState.WAITING }
        val ready = rows.count { it.state == GrowthState.READY }
        val analysed = rows.count { it.state == GrowthState.READY || it.state == GrowthState.PAUSED || it.state == GrowthState.GROWING }
        val pending = rows.filter { it.state == GrowthState.GROWING || it.state == GrowthState.UNANALYSED }.mapNotNull { it.readyInMs?.takeIf { ms -> ms > 0 } }
        val next = pending.minOrNull(); val last = pending.maxOrNull()?.takeIf { pending.size > 1 }
        val (value, color) = when {
            ready > 0 -> "$ready ready now" to MenuTheme.YELLOW
            next != null -> "Next in ${GreenhousePlanner.age(next)}" to GREEN
            else -> "$growing of ${rows.size} growing" to if (growing > 0) GREEN else TEXT
        }
        val lines = ArrayList<String>()
        if (ready > 0 && next != null) lines += "Next in ${GreenhousePlanner.age(next)}" + (last?.let { ", all in ${GreenhousePlanner.age(it)}" } ?: "")
        else if (last != null) lines += "All ready in ${GreenhousePlanner.age(last)}"
        lines += "$growing of ${rows.size} growing, " + if (growing > analysed) "${growing - analysed} not analysed" else "all analysed"
        lines += (if (plot.plantedAt > 0) "Planted ${GreenhousePlanner.age(now - plot.plantedAt)} ago" else "Planting time not seen yet") + ", ${"%.1f".format(cycle)}h cycle (est.)"
        if (analysed == 0 && growing > 0) lines += "Analyze them with the Plant Diagnostics Tool to see their stage"
        return tile("Growing", value, color, 32, lines)
    }

    private fun estimated(perHour: Double, share: Int) = tile("Estimated", "${GreenhousePlanner.signed(perHour * 24)}/day", GREEN, share,
        listOf("${GreenhousePlanner.signed(perHour)}/h", "${GreenhousePlanner.signed(perHour * 24 * 7)}/week, ${GreenhousePlanner.signed(perHour * 24 * 30)}/month"))

    private fun harvested(totals: HarvestTotals, share: Int) = if (totals.count == 0) tile("Harvested", "—", MUTED, share, listOf("No harvests recorded yet")) else
        tile("Harvested", "${GreenhousePlanner.signed(totals.week)} last 7d", TEXT, share, listOf(
            "24h ${GreenhousePlanner.signed(totals.day)}, 30d ${GreenhousePlanner.signed(totals.month)}",
            "${GreenhousePlanner.signed(totals.total)} total, ${totals.count} harvest${if (totals.count == 1) "" else "s"}"))

    /** One number that matters, with its context in small print underneath. */
    private fun tile(title: String, value: String, color: Int, share: Int, lines: List<String>) = UIContainers.verticalFlow(Sizing.fill(share), Sizing.content()).apply {
        gap(2); padding(Insets.of(5)); surface(MenuTheme.tintedPanel { MenuTheme.alpha(MenuTheme.blend(MenuTheme.CARD, MenuTheme.PANEL, 60), 80) })
        child(label(title, MUTED).apply { fontSize = 8f })
        child(label(value, color).apply { fontSize = 13f; horizontalSizing(Sizing.fill()) })
        lines.forEach { child(label(it, MUTED).apply { fontSize = 8f; horizontalSizing(Sizing.fill()) }) }
    }

    private fun removeButton(plot: GreenhousePlanner.Plot) = button("Remove", 60) {
        navigateMenu(MenuConfirmScreen(this, "Remove plot ${plot.name}?", "Its anchor, layout, locks and growth readings are forgotten. Harvest history is kept.", "Remove") {
            GreenhousePlanner.removePlot(plot.name)
        })
    }.tooltip(Component.literal("Forget this plot (asks first)"))

    /** Chat output is hidden behind the menu, so actions that answer in chat close it first. */
    private fun chat(action: () -> Unit) { onClose(); MC.instance.execute(action) }

    private fun remindersLabel(on: Boolean = GreenhouseConfig.discordReminders) = if (on) "Reminders: On" else "Reminders: Off"
    private fun Int?.orZero() = this ?: 0
    private fun row() = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content()).apply { verticalAlignment(VerticalAlignment.CENTER) }
    private fun label(text: String, color: Int = TEXT) = MenuLabel(Component.literal(text)).apply { color(Color.ofRgb(color)); shadow(MenuTheme.SHADOW) }
    private fun button(text: String, width: Int, action: (ButtonComponent) -> Unit) = MenuButton(Component.literal(text), action).apply {
        sizing(Sizing.fixed(width), Sizing.fixed(20)); themed(); textShadow(false)
    }
    private fun actions(buttons: List<UIComponent>) = row().apply { gap(4); children(buttons) }
    private fun card(stripe: Int) = UIContainers.verticalFlow(Sizing.fill(), Sizing.content()).apply {
        padding(Insets.of(6)); gap(5)
        surface(MenuTheme.tintedPanel { MenuTheme.CARD }.and(Surface { graphics, component ->
            graphics.fill(component.x(), component.y(), component.x() + 2, component.y() + component.height(), MenuTheme.alpha(stripe))
        }))
    }

    private companion object {
        val TEXT get() = MenuTheme.TEXT
        val MUTED get() = MenuTheme.MUTED
        val GREEN get() = MenuTheme.GREEN
    }
}

/**
 * The plot's 10×10 layout drawn from the pinned plan and the last scan; hovering a slot names it. Rows run left to
 * right (row 1 at the left) and columns top to bottom, so the bottom of the grid is the greenhouse entrance.
 */
internal class GreenhouseGrid(
    private val layout: Layout, private val status: String, private val cell: Int = 9,
    private val locked: BooleanArray? = null, private val onCell: ((Int) -> Unit)? = null,
    /** A placement's growth from the Plant Diagnostics Tool, or null when not analysed. */
    private val growth: (Placement) -> GrowthEstimate? = { null },
) : BaseUIComponent() {
    private var hoveredIndex = -1

    init { sizing(Sizing.fixed(cell * GREENHOUSE_SIZE + 1), Sizing.fixed(cell * GREENHOUSE_SIZE + 1)) }

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        g.roundedRect(x(), y(), width(), height(), 2.0, MenuTheme.alpha(MenuTheme.BORDER, 35))
        for (index in 0 until GREENHOUSE_CELLS) {
            if (layout.cells[index] != null) continue
            g.roundedRect(x() + index / GREENHOUSE_SIZE * cell + 1, y() + index % GREENHOUSE_SIZE * cell + 1, cell - 1, cell - 1, 1.0, MenuTheme.alpha(MenuTheme.MUTED, 12))
        }
        val dot = if (cell >= 14) 5 else 3
        for (placement in layout.placements) {
            val first = placement.row * GREENHOUSE_SIZE + placement.col
            val code = status.getOrNull(first)?.let(CellStatus::of)
            val px = x() + placement.row * cell + 1; val py = y() + placement.col * cell + 1
            val size = placement.size * cell - 1
            val strength = when (code) {
                null -> 60
                CellStatus.DONE, CellStatus.TARGET_GROWING -> 95
                CellStatus.MISSING_CROP, CellStatus.TARGET_WAITING -> 30
                else -> 60
            }
            val color = if (code == CellStatus.WRONG_CROP) MenuTheme.RED else GreenhousePlanner.plantColor(placement.plantId)
            g.roundedRect(px, py, size, size, 1.5, MenuTheme.alpha(color, strength))
            val reading = if (code == CellStatus.TARGET_GROWING) growth(placement) else null
            if (reading == null) {
                if (placement.role == Role.TARGET) g.roundedBorder(px, py, size, size, 1.5, 1.0, MenuTheme.alpha(MenuTheme.TEXT, 70))
                if (code == CellStatus.TARGET_GROWING) g.roundedRect(px + (size - dot) / 2, py + (size - dot) / 2, dot, dot, dot / 2.0, MenuTheme.alpha(MenuTheme.TEXT))
            } else {
                // An analysed mutation fills its slot from the bottom as it grows and turns yellow once fully grown.
                val done = reading.ready
                val level = if (done || reading.stages == 0) size - 2 else ((size - 2) * (reading.stage / reading.stages)).toInt().coerceIn(1, size - 3)
                g.fill(px + 1, py + size - 1 - level, px + size - 1, py + size - 1, MenuTheme.alpha(if (done) MenuTheme.YELLOW else MenuTheme.GREEN, 75))
                g.roundedBorder(px, py, size, size, 1.5, 1.0, MenuTheme.alpha(if (done) MenuTheme.YELLOW else MenuTheme.TEXT, 90))
            }
            val wrongSoil = (0 until placement.size).any { r -> (0 until placement.size).any { c ->
                status.getOrNull(first + r * GREENHOUSE_SIZE + c) == CellStatus.WRONG_SOIL.code
            } }
            if (wrongSoil) g.roundedBorder(px, py, size, size, 1.5, 1.0, MenuTheme.alpha(MenuTheme.RED))
        }
        locked?.forEachIndexed { index, isLocked ->
            if (!isLocked) return@forEachIndexed
            val px = x() + index / GREENHOUSE_SIZE * cell + 1; val py = y() + index % GREENHOUSE_SIZE * cell + 1
            g.roundedRect(px, py, cell - 1, cell - 1, 1.0, MenuTheme.alpha(MenuTheme.PANEL, 200))
            val color = io.wispforest.owo.ui.core.Color.ofArgb(MenuTheme.alpha(MenuTheme.RED, 160))
            g.drawLine(px + 2, py + 2, px + cell - 4, py + cell - 4, 1.0, color); g.drawLine(px + cell - 4, py + 2, px + 2, py + cell - 4, 1.0, color)
        }
        val inside = mouseX in x() until x() + width() - 1 && mouseY in y() until y() + height() - 1
        val index = if (inside) greenhouseCellAt(mouseX - x(), mouseY - y(), cell) else -1
        if (index != hoveredIndex) {
            hoveredIndex = index
            if (index < 0) tooltip(null as List<ClientTooltipComponent>?) else tooltip(Component.literal(describe(index)))
        }
    }

    override fun onMouseDown(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean): Boolean {
        val action = onCell ?: return super.onMouseDown(click, doubled)
        if (click.button() != 0) return super.onMouseDown(click, doubled)
        val index = greenhouseCellAt(click.x().toInt(), click.y().toInt(), cell)
        if (index !in 0 until GREENHOUSE_CELLS) return false
        action(index); hoveredIndex = -2
        return true
    }

    private fun describe(index: Int): String {
        val plan = layout.cells[index]
        val where = "Row ${index / GREENHOUSE_SIZE + 1}, column ${index % GREENHOUSE_SIZE + 1}" +
            if (locked?.getOrNull(index) == true) " · locked (click to unlock)" else if (onCell != null) " · click to lock" else ""
        if (plan == null) return "$where\nUnused"
        val name = GreenhousePlanner.catalog?.get(plan.plantId)?.name ?: plan.plantId
        val state = when (status.getOrNull(index)?.let(CellStatus::of)) {
            CellStatus.DONE -> "Planted"
            CellStatus.MISSING_CROP -> "Not planted yet"
            CellStatus.WRONG_CROP -> "A different crop is planted here"
            CellStatus.WRONG_SOIL -> "Needs ${GreenhousePlanner.soilName(plan.soil)} soil"
            CellStatus.TARGET_WAITING -> "Empty, waiting for the mutation"
            CellStatus.TARGET_GROWING -> "Mutation growing"
            else -> "Not checked yet"
        }
        val placement = layout.placements.firstOrNull { index / GREENHOUSE_SIZE - it.row in 0 until it.size && index % GREENHOUSE_SIZE - it.col in 0 until it.size }
        val stage = placement?.let(growth)?.let { estimate -> when {
            estimate.stages == 0 -> "\nFully grown"
            else -> "\nAbout stage ${"%.1f".format(estimate.stage)} of ${estimate.stages}" +
                if (estimate.ready) ", ready" else if (estimate.paused) ", stopped until you ${GreenhousePlanner.pauseAction(plan.plantId)}" else ""
        } }.orEmpty()
        return "$where\n$name (${if (plan.role == Role.TARGET) "target" else "ingredient"}) on ${GreenhousePlanner.soilName(plan.soil)}\n$state$stage"
    }
}

internal enum class GrowthState { READY, PAUSED, GROWING, UNANALYSED, WAITING }

/** One target slot's growth; [readyInMs] comes from a reading, or is an estimate from the spawn time when not analysed. */
internal data class GrowthRow(val where: String, val plantId: String, val state: GrowthState, val stage: Double, val stages: Int, val readyInMs: Long?,
                            val waitInMs: Long? = null)

/** Target slots as aligned rows: slot, stage, a progress bar and the time until fully grown. */
internal class GrowthRows(private val rows: List<GrowthRow>) : BaseUIComponent() {
    private val size = 8.0
    private val line = MenuCanvas.lineHeight(size) + 2

    init { sizing(Sizing.fill(), Sizing.fixed(rows.size * line)) }

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        val slot = MenuCanvas.textWidth("R10 C10", size = size) + 4
        val stage = MenuCanvas.textWidth("20/20", bold = true, size = size) + 6
        val text = MenuCanvas.textWidth("not analysed, may be ready", size = size)
        val bar = (width() - 11 - slot - stage - 8 - text).toInt().coerceAtLeast(20)
        rows.forEachIndexed { i, row ->
            val top = y() + i * line
            val (color, note) = when (row.state) {
                GrowthState.READY -> MenuTheme.YELLOW to "ready now"
                GrowthState.PAUSED -> MenuTheme.RED to "stopped, ${GreenhousePlanner.pauseAction(row.plantId)}"
                GrowthState.GROWING -> MenuTheme.GREEN to (row.readyInMs?.let { "ready in ${GreenhousePlanner.age(it)}" }
                    ?: row.waitInMs?.let { "${GreenhousePlanner.pauseAction(row.plantId).removeSuffix(" it")} in ${GreenhousePlanner.age(it)}" } ?: "analyze again")
                GrowthState.UNANALYSED -> MenuTheme.MUTED to "not analysed" + (row.readyInMs?.let { if (it < 60_000) ", may be ready" else ", ~${GreenhousePlanner.age(it)}" } ?: "")
                GrowthState.WAITING -> MenuTheme.MUTED to "not spawned"
            }
            val faded = row.state == GrowthState.WAITING
            g.roundedRect(x(), top + 2, 7, 7, 1.5, MenuTheme.alpha(GreenhousePlanner.plantColor(row.plantId), if (faded) 35 else 100))
            g.styledText(listOf(MenuCanvas.Run(row.where, MenuTheme.alpha(if (faded) MenuTheme.MUTED else MenuTheme.TEXT))), x() + 11.0, top.toDouble(), size)
            val stageText = if (row.stages == 0) "—" else "${row.stage.toInt().coerceAtMost(row.stages)}/${row.stages}"
            g.styledText(listOf(MenuCanvas.Run(stageText, MenuTheme.alpha(if (row.stages == 0) MenuTheme.MUTED else color), bold = row.stages > 0)), x() + 11 + slot, top.toDouble(), size)
            val bx = (x() + 11 + slot + stage).toInt(); val by = top + 3
            g.roundedRect(bx, by, bar, 5, 2.5, MenuTheme.alpha(MenuTheme.blend(MenuTheme.BORDER, MenuTheme.MUTED, 30), if (faded) 30 else 70))
            if (row.stages > 0) {
                val fill = (bar * (row.stage / row.stages)).toInt().coerceIn(5, bar)
                g.roundedRect(bx, by, fill, 5, 2.5, MenuTheme.alpha(color, 90))
            }
            g.styledText(listOf(MenuCanvas.Run(note, MenuTheme.alpha(color))), bx + bar + 8.0, top.toDouble(), size)
        }
    }
}

/** Colour chips for every crop in the layout, wrapped to the available width; crops planted once share one group. */
internal class GreenhouseLegend(layout: Layout) : BaseUIComponent() {
    private val size = 9.0
    private val chipGap = 10.0
    private val entries: List<Pair<Int?, List<MenuCanvas.Run>>>

    init {
        val catalog = GreenhousePlanner.catalog
        val groups = layout.placements.groupBy { it.plantId to it.role }.entries.sortedWith(compareBy({ it.key.second != Role.TARGET }, { -it.value.size }))
        val many = groups.filter { it.value.size > 1 || it.key.second == Role.TARGET }
        val once = groups - many.toSet()
        entries = many.map { (key, placements) ->
            val name = catalog?.get(key.first)?.name ?: key.first
            GreenhousePlanner.plantColor(key.first) to listOf(MenuCanvas.Run("$name ×${placements.size}", MenuTheme.alpha(MenuTheme.TEXT), bold = key.second == Role.TARGET))
        } + (if (once.isEmpty()) emptyList() else listOf(null to listOf(MenuCanvas.Run("1 each", MenuTheme.alpha(MenuTheme.MUTED))))) +
            once.map { (key, _) -> GreenhousePlanner.plantColor(key.first) to listOf(MenuCanvas.Run(catalog?.get(key.first)?.name ?: key.first, MenuTheme.alpha(MenuTheme.TEXT))) }
        sizing(Sizing.fill(), Sizing.content())
    }

    private fun chipWidth(entry: Pair<Int?, List<MenuCanvas.Run>>) = (if (entry.first == null) 0.0 else 11.0) + entry.second.sumOf { MenuCanvas.textWidth(it.text, it.bold, size = size) }
    private fun lineHeight() = MenuCanvas.lineHeight(size) + 2

    /** Pack chips left to right; each call returns the row index for every chip. */
    private fun rows(width: Int): IntArray {
        var x = 0.0; var row = 0
        return IntArray(entries.size) { i ->
            val w = chipWidth(entries[i])
            if (x > 0 && x + w > width) { x = 0.0; row++ }
            x += w + chipGap
            row
        }
    }

    override fun determineHorizontalContentSize(sizing: Sizing) = entries.maxOfOrNull { kotlin.math.ceil(chipWidth(it)).toInt() } ?: 0
    override fun determineVerticalContentSize(sizing: Sizing) = ((rows(width()).lastOrNull() ?: -1) + 1) * lineHeight()

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        val placed = rows(width())
        var x = 0.0; var row = -1
        entries.forEachIndexed { i, entry ->
            if (placed[i] != row) { row = placed[i]; x = 0.0 }
            val top = y() + row * lineHeight()
            entry.first?.let { g.roundedRect((x() + x).toInt(), top + 2, 7, 7, 1.5, MenuTheme.alpha(it)); }
            g.styledText(entry.second, x() + x + if (entry.first == null) 0.0 else 11.0, top.toDouble(), size)
            x += chipWidth(entry) + chipGap
        }
    }
}

/** Daily harvest profit as static bars; losses hang below the zero line. */
internal class ProfitBars(private val values: DoubleArray) : BaseUIComponent() {
    private var hoveredIndex = -2
    init { sizing(Sizing.fill(), Sizing.fixed(48)) }

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        g.roundedRect(x(), y(), width(), height(), 2.0, MenuTheme.alpha(MenuTheme.BORDER, 20))
        val high = values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val low = values.minOrNull()?.coerceAtMost(0.0) ?: 0.0
        val range = (high - low).takeIf { it > 0 } ?: return
        val zero = y() + 3 + ((high / range) * (height() - 6)).toInt()
        val slot = (width() - 4) / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, value ->
            if (value == 0.0) return@forEachIndexed
            val size = (kotlin.math.abs(value) / range * (height() - 6)).toInt().coerceAtLeast(1)
            val top = if (value > 0) zero - size else zero
            g.roundedRect(x() + 2 + i * slot + 1, top, (slot - 2).coerceAtLeast(1), size, 1.0, MenuTheme.alpha(if (value > 0) MenuTheme.GREEN else MenuTheme.RED, 80))
        }
        g.fill(x() + 2, zero, x() + width() - 2, zero + 1, MenuTheme.alpha(MenuTheme.MUTED, 50))
        val index = if (mouseX in x() until x() + width() && mouseY in y() until y() + height()) ((mouseX - x() - 2) / slot.coerceAtLeast(1)).coerceIn(0, values.size - 1) else -1
        if (index != hoveredIndex) {
            hoveredIndex = index
            val ago = values.size - 1 - index
            if (index < 0) tooltip(null as List<ClientTooltipComponent>?) else tooltip(Component.literal("${if (ago == 0) "Today" else "$ago days ago"}: ${GreenhousePlanner.signed(values[index])}"))
        }
    }
}

/** The data cell index under a point inside the rotated grid: screen x runs along data rows, screen y along columns. */
internal fun greenhouseCellAt(dx: Int, dy: Int, cell: Int) = dx / cell * GREENHOUSE_SIZE + dy / cell

/** A small static pill such as "Nearest". */
internal class GreenhouseBadge(private val text: String, private val color: Int) : BaseUIComponent() {
    private val size = 7.0
    init { sizing(Sizing.fixed(kotlin.math.ceil(MenuCanvas.textWidth(text, bold = true, size = size)).toInt() + 10), Sizing.fixed(MenuCanvas.lineHeight(size) + 3)) }

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        val radius = height() / 2.0
        g.roundedRect(x(), y(), width(), height(), radius, MenuTheme.alpha(color, 22))
        g.roundedBorder(x(), y(), width(), height(), radius, 1.0, MenuTheme.alpha(color, 75))
        g.styledText(listOf(MenuCanvas.Run(text, MenuTheme.alpha(color), bold = true)), x() + 5.0, y() + 1.5, size)
    }
}

/** Progress as a static track; the numbers beside it carry the exact values. */
internal class GreenhouseProgressBar(private val filled: Long, private val total: Long, private val color: Int) : BaseUIComponent() {
    init { sizing(Sizing.fill(), Sizing.fixed(5)) }

    override fun draw(graphics: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
        val g = graphics as MenuGraphics
        val radius = height() / 2.0
        g.roundedRect(x(), y(), width(), height(), radius, MenuTheme.alpha(MenuTheme.blend(MenuTheme.BORDER, MenuTheme.MUTED, 30), 70))
        val fraction = if (total <= 0) 0.0 else (filled.toDouble() / total).coerceIn(0.0, 1.0)
        val fill = (width() * fraction).toInt()
        if (fill > 0) g.roundedRect(x(), y(), maxOf(fill, height()), height(), radius, MenuTheme.alpha(color, 90))
    }
}
