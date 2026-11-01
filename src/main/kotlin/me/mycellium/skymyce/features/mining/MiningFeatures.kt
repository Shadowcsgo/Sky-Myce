package me.mycellium.skymyce.features.mining

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.mycellium.skymyce.SkyMyce
import me.mycellium.skymyce.SkyMyceModule
import me.mycellium.skymyce.api.events.ChatChannel
import me.mycellium.skymyce.config.Config
import me.mycellium.skymyce.config.mining.MiningConfig
import me.mycellium.skymyce.utils.AtomicJsonFile
import me.mycellium.skymyce.utils.StateWriter
import me.mycellium.skymyce.utils.Utils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import tech.thatgravyboat.skyblockapi.utils.Scheduling
import me.mycellium.skymyce.hud.HudTheme
import me.mycellium.skymyce.hud.hudPanel
import me.mycellium.skymyce.hud.updateTextLines
import me.mycellium.skymyce.hud.widget.Anchor
import me.mycellium.skymyce.hud.widget.Widget
import me.mycellium.skymyce.utils.MC
import me.mycellium.skymyce.utils.PlayerUtils.sendCommand
import me.mycellium.skymyce.utils.PlayerUtils.sendMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import tech.thatgravyboat.skyblockapi.api.area.mining.CommissionArea
import tech.thatgravyboat.skyblockapi.api.area.mining.CommissionsAPI
import tech.thatgravyboat.skyblockapi.api.area.mining.GlaciteAPI
import tech.thatgravyboat.skyblockapi.api.area.mining.MiningBlock
import tech.thatgravyboat.skyblockapi.api.area.mining.MiningBlockFamily
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyIn
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.info.TabWidget
import tech.thatgravyboat.skyblockapi.api.events.info.TabWidgetChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.location.IslandChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.render.RenderWorldEvent
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.api.location.SkyBlockIsland
import tech.thatgravyboat.skyblockapi.api.profile.party.PartyAPI
import tech.thatgravyboat.skyblockapi.utils.Scheduling.schedule
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object MiningFeatures : SkyMyceModule() {
    val crystalRegex = Regex("^\\s*✦ CRYSTAL FOUND \\((\\d+)/(\\d+)\\)$")

    var totalTime = 0L
    var timeSince = System.currentTimeMillis()

    const val CH_LOCK_DAY = 20.5
    const val CH_CLOSE_DAY = 35
    private val crystalHollowsContent by lazy { hudPanel(decorated = false) }

    val crystalHollowsWidget = Widget("crystal_hollows_info", "CH Info", 200, 200, 1f, Anchor.TOP_LEFT) {
        if (!MiningConfig.crystalHollowsInfo) return@Widget null
        if (LocationAPI.island != SkyBlockIsland.CRYSTAL_HOLLOWS) return@Widget null

        val time = (MC.instance.level?.gameTime ?: 0L) - 60240000 // 2510 days in ticks
        val day = ((time / 20.0) / 60.0) / 20.0 // 20 ticks, 60 seconds, 20 minutes
        val lockTimeRemaining = (((CH_LOCK_DAY * 24000) - time) / 20).seconds
        val closeTimeRemaining = (((CH_CLOSE_DAY * 24000) - time) / 20).seconds

        val dayFormatted = when {
            (day > CH_CLOSE_DAY) -> "§c${floor(day * 100) / 100} (CLOSING)"
            (day > CH_LOCK_DAY) -> "§e${floor(day * 100) / 100} (LOCKED)"
            else -> "§a${floor(day * 100) / 100}"
        }

        val lines = mutableListOf<String>()
        lines += "§7Day: §e$dayFormatted"
        lines += "§7Player count: (§e${LocationAPI.playerCount}§7/§a${LocationAPI.maxPlayercount}§7)"
        lines += "§7Lobby Time: §e${(System.currentTimeMillis() - timeSince).milliseconds.inWholeSeconds.seconds}"
        lines += "§7Session Time: §e${(totalTime + System.currentTimeMillis() - timeSince).milliseconds.inWholeSeconds.seconds}"
        if (lockTimeRemaining > 0.seconds) {
            lines += "§7Time to lock: §e${lockTimeRemaining.inWholeSeconds.seconds}"
        } else if (closeTimeRemaining > 0.seconds && LocationAPI.playerCount > 3) {
            lines += "§7Time to close: §e${closeTimeRemaining.inWholeSeconds.seconds}"
        } else {
            lines += "§cLobby Closing!"
        }

        crystalHollowsContent.updateTextLines(lines)
    }

    private const val SPREAD_COLOR = 0x55FFFF
    private const val BLAST_COLOR = 0xFFAA00
    private const val BLAST_SCAN = 12

    private var miningSpread: Int? = null
    private var gemstoneSpread: Int? = null
    private var nextBlock: Pair<BlockPos, MiningBlock>? = null
    private var blast: Pair<BlockPos, List<BlockPos>>? = null
    private var ticks = 0

    @Subscription
    fun onTabWidget(event: TabWidgetChangeEvent) {
        if (event.widget != TabWidget.STATS) return
        miningSpread = parseStat(event.new, "Mining Spread")
        gemstoneSpread = parseStat(event.new, "Gemstone Spread")
    }

    @Subscription
    fun onTick(event: TickEvent) {
        ticks++
        if (MiningConfig.spreadPredictor) predictSpread() else nextBlock = null
        // The blast scan covers ~15k blocks, so it refreshes once a second rather than every tick.
        if (!MiningConfig.pickobulusHelper) blast = null else if (ticks % 20 == 0) blast = findBlast()
        if (Config.devMode && MiningConfig.recordSpread) recordSpread() else { snapshot = null; pending = null }
    }

    // ---- Developer spread recorder: raw samples for working out how Hypixel picks spread blocks ----
    // It diffs world snapshots on the client thread; SkyBlockAPI's mined events come from packet handling and missed every break.

    private const val SAMPLE_RADIUS = 3
    private const val SAMPLE_WINDOW = 10
    private const val MAX_SAMPLES = 1500

    private class Snapshot(val centre: BlockPos, val name: String, val blocks: List<Block>, val sample: JsonObject) {
        var tick = 0
        val brokenAt = HashMap<Int, Int>()
    }
    private var snapshot: Snapshot? = null
    private var pending: Snapshot? = null
    private val sampleFile = AtomicJsonFile(SkyMyce.configPath.resolve("mining_spread_samples.json"), 4 * 1024 * 1024)
    private val sampleWriter = StateWriter<JsonArray>({ sampleFile.write(it) }, { action -> Scheduling.schedule(500.milliseconds) { action() } },
        { SkyMyce.logger.warn("Could not save Mining Spread samples; previous file retained") })
    private val samples by lazy { runCatching { sampleFile.read()?.asJsonArray }.getOrNull() ?: JsonArray() }

    private fun gone(block: Block) = block == Blocks.AIR || block == Blocks.BEDROCK
    private fun around(centre: BlockPos) = BlockPos.betweenClosed(centre.offset(-SAMPLE_RADIUS, -SAMPLE_RADIUS, -SAMPLE_RADIUS), centre.offset(SAMPLE_RADIUS, SAMPLE_RADIUS, SAMPLE_RADIUS))

    private fun recordSpread() {
        val level = MC.instance.level ?: return
        val player = MC.instance.player ?: return
        // The block being mined just broke: watch its surroundings for the spread.
        snapshot?.takeIf { pending == null && gone(level.getBlockState(it.centre).block) }?.let { pending = it.also { s -> s.tick = ticks }; snapshot = null }
        pending?.let { shot ->
            around(shot.centre).forEachIndexed { i, pos -> if (i !in shot.brokenAt && !gone(shot.blocks[i]) && gone(level.getBlockState(pos).block)) shot.brokenAt[i] = ticks - shot.tick }
            if (ticks >= shot.tick + SAMPLE_WINDOW) { finishSample(shot); pending = null }
            return
        }
        val hit = (MC.instance.hitResult as? BlockHitResult)?.takeIf { it.type == HitResult.Type.BLOCK && MC.instance.options.keyAttack.isDown }
        val centreBlock = hit?.let { level.getBlockState(it.blockPos).block }
        if (hit == null || centreBlock == null || gone(centreBlock)) { snapshot = null; return }
        val type = MiningBlock.currentlyActiveBlocks.firstOrNull { centreBlock in it.blocks }
        val eye = player.eyePosition.subtract(Vec3.atCenterOf(hit.blockPos))
        val look = player.lookAngle
        snapshot = Snapshot(hit.blockPos.immutable(), type?.name ?: BuiltInRegistries.BLOCK.getKey(centreBlock).toString(),
            around(hit.blockPos).map { level.getBlockState(it).block }, JsonObject().apply {
            addProperty("island", LocationAPI.island?.name)
            addProperty("block", type?.name)
            addProperty("family", type?.family?.name)
            miningSpread?.let { addProperty("miningSpread", it) }
            gemstoneSpread?.let { addProperty("gemstoneSpread", it) }
            addProperty("face", hit.direction.name)
            add("eye", JsonArray().apply { add(eye.x); add(eye.y); add(eye.z) })
            add("look", JsonArray().apply { add(look.x); add(look.y); add(look.z) })
        })
    }

    private fun finishSample(shot: Snapshot) {
        val centreIndex = shot.blocks.size / 2
        val broken = shot.brokenAt.filterKeys { it != centreIndex }
        val (palette, grid) = encodeGrid(shot.blocks.map { BuiltInRegistries.BLOCK.getKey(it).toString() }) ?: return
        val side = SAMPLE_RADIUS * 2 + 1
        shot.sample.apply {
            addProperty("t", System.currentTimeMillis())
            add("palette", JsonArray().apply { palette.forEach(::add) })
            addProperty("grid", grid)
            add("broken", JsonArray().apply { broken.forEach { (i, dt) -> add(JsonArray().apply {
                add(i % side - SAMPLE_RADIUS); add(i / side % side - SAMPLE_RADIUS); add(i / (side * side) - SAMPLE_RADIUS); add(dt)
            }) } })
        }
        samples.add(shot.sample)
        while (samples.size() > MAX_SAMPLES) samples.remove(0)
        sampleWriter.submit(samples.deepCopy())
        Utils.displayDevMessage("Spread sample ${samples.size()}: ${shot.name} took ${broken.size} more")
    }

    private fun predictSpread() {
        val level = MC.instance.level ?: return
        val player = MC.instance.player ?: return
        nextBlock = nextBlock?.takeIf { (pos, type) -> level.getBlockState(pos).block in type.blocks }
        if (!MC.instance.options.keyAttack.isDown) return
        val mining = (MC.instance.hitResult as? BlockHitResult)?.takeIf { it.type == HitResult.Type.BLOCK }?.blockPos ?: return
        val type = MiningBlock.currentlyActiveBlocks.firstOrNull { level.getBlockState(mining).block in it.blocks } ?: return
        fun sameType(pos: BlockPos) = level.getBlockState(pos).block in type.blocks
        val eye = player.eyePosition
        val reach = player.blockInteractionRange()
        val r = ceil(reach).toInt()
        val origin = BlockPos.containing(eye)
        // Only blocks with an open face (counting the one being broken) can be aimed at straight away.
        val candidates = BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r)).mapNotNull { pos ->
            pos.immutable().takeIf { it != mining && sameType(it) && Vec3.atCenterOf(it).distanceTo(eye) <= reach &&
                Direction.entries.any { d -> it.relative(d).let { n -> n == mining || level.getBlockState(n).isAir } } }
        }
        val spread = spreadFor(type.family == MiningBlockFamily.GEMSTONES, miningSpread, gemstoneSpread, MiningConfig.spreadFallback, MiningConfig.gemstoneSpreadFallback)
        val look = player.lookAngle
        nextBlock = bestSpreadFollowUp(mining, candidates, ::sameType, spread.toDouble()) { 1 - look.dot(Vec3.atCenterOf(it).subtract(eye).normalize()) }
            ?.let { it to type }
    }

    private fun findBlast(): Pair<BlockPos, List<BlockPos>>? {
        if (!GlaciteAPI.inGlaciteTunnels()) return null
        val level = MC.instance.level ?: return null
        val player = MC.instance.player ?: return null
        val ores = CommissionsAPI.commissions.filter { it.area == CommissionArea.GLACITE_TUNNELS && it.progress < 1f }.mapNotNull { collectorOre(it.name) }.toSet()
        val blocks = MiningBlock.entries.filter { it.name.substringAfterLast('_') in ores }.flatMap { it.blocks }.toSet()
        if (blocks.isEmpty()) return null
        val eye = player.eyePosition
        val origin = BlockPos.containing(eye)
        val targets = BlockPos.betweenClosed(origin.offset(-BLAST_SCAN, -BLAST_SCAN, -BLAST_SCAN), origin.offset(BLAST_SCAN, BLAST_SCAN, BLAST_SCAN))
            .mapNotNull { pos -> pos.immutable().takeIf { level.getBlockState(it).block in blocks } }
        val radius = MiningConfig.pickobulusRadius
        // The thrown pickaxe lands on the first block in your line of sight, so only a visible centre is reachable.
        // shortcut: only target blocks are tried as centres; a hard stone block between two veins could score higher.
        val centre = rankBlastCentres(targets, radius).take(64).firstOrNull { (pos, _) ->
            level.clip(ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).blockPos == pos
        }?.first ?: return null
        return centre to targets.filter { it.distSqr(centre) <= radius * radius }
    }

    @Subscription
    fun render(event: RenderWorldEvent.AfterTranslucent) {
        val next = nextBlock?.first
        val blast = blast
        if (next == null && blast == null) return
        MC.instance.levelRenderer.collectPerFrameGizmos().use {
            next?.let { Gizmos.cuboid(AABB(it).inflate(0.004), GizmoStyle.stroke(HudTheme.alpha(SPREAD_COLOR, 100), 3f)).setAlwaysOnTop() }
            blast?.let { (centre, hits) ->
                hits.forEach { Gizmos.cuboid(AABB(it).inflate(0.002), GizmoStyle.fill(HudTheme.alpha(BLAST_COLOR, 25))) }
                Gizmos.cuboid(AABB(centre).inflate(0.004), GizmoStyle.stroke(HudTheme.alpha(BLAST_COLOR, 100), 3f)).setAlwaysOnTop()
                Gizmos.billboardText("Pickobulus · ~${hits.size} blocks", Vec3.atCenterOf(centre).add(0.0, 0.9, 0.0),
                    TextGizmo.Style.forColorAndCentered(HudTheme.alpha(BLAST_COLOR, 100)).withScale(0.4f)).setAlwaysOnTop()
            }
        }
    }

    @Subscription
    fun onIslandChange(event: IslandChangeEvent) {
        nextBlock = null
        blast = null
        if (event.old == SkyBlockIsland.CRYSTAL_HOLLOWS) {
            totalTime += System.currentTimeMillis() - timeSince
        }
        if (event.new == SkyBlockIsland.CRYSTAL_HOLLOWS) {
            timeSince = System.currentTimeMillis()
        }
    }

    @Subscription
    @OnlyIn(SkyBlockIsland.CRYSTAL_HOLLOWS)
    fun onChat(event: ChatReceivedEvent.Pre) {
        if (MiningConfig.autoNucleusWarp && crystalRegex.matches(event.text)) {
            schedule(Random.nextDouble(MiningConfig.autoNucleusWarpMin, MiningConfig.autoNucleusWarpMax).seconds) {
                sendCommand("warp nuc", true)
            }
        }

        if (MiningConfig.balNotify && PartyAPI.inParty) {
            when (event.text) {
                "The ground starts to shake and the lava bubbles..." -> sendMessage("Bal is spawning!", ChatChannel.PARTY)

                "The boss's outer shell looks to be weakening!" -> sendMessage("Bal 75% HP", ChatChannel.PARTY)

                "Half way there! The boss is starting to become weaker!" -> sendMessage("Bal 50% HP", ChatChannel.PARTY)

                "Nearly there! The boss is shaking, it can't last much longer!" -> sendMessage("Bal 25% HP", ChatChannel.PARTY)

                "The boss looks weak and tired and retreats into the lava..." -> sendMessage("Bal dead!", ChatChannel.PARTY)
            }
        }
    }
}

internal fun parseStat(lines: List<String>, stat: String) =
    lines.firstNotNullOfOrNull { Regex("$stat: \\D*([\\d,]+)").find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }

/** The ore of a collector commission, e.g. "Umber Collector" -> UMBER and "Onyx Gemstone Collector" -> ONYX, matching [MiningBlock] name suffixes. */
internal fun collectorOre(name: String) = name.takeIf { it.endsWith("Collector") }?.substringBefore(' ')?.uppercase()

/** Mining Spread breaks [spread]/100 extra blocks on average, shared among the broken block's same-type face neighbours. */
// shortcut: assumes spread takes same-type face neighbours evenly; revisit if Hypixel's pick rule turns out different.
internal fun spreadTakeChance(spread: Double, sameTypeNeighbours: Int) =
    if (sameTypeNeighbours <= 0) 0.0 else (spread / 100.0 / sameTypeNeighbours).coerceIn(0.0, 1.0)

/** The candidate worth mining once [mining] breaks: likely to survive its spread, then the most expected spread of its own, then cheapest to aim at. */
internal fun bestSpreadFollowUp(mining: BlockPos, candidates: Collection<BlockPos>, sameType: (BlockPos) -> Boolean, spread: Double, aimCost: (BlockPos) -> Double): BlockPos? {
    fun neighbours(pos: BlockPos) = Direction.entries.map(pos::relative).filter { it != mining && sameType(it) }
    val take = spreadTakeChance(spread, neighbours(mining).size)
    fun survives(pos: BlockPos) = if (pos.distManhattan(mining) == 1) 1 - take else 1.0
    fun score(pos: BlockPos) = survives(pos) * (1 + minOf(spread / 100.0, neighbours(pos).sumOf(::survives)))
    return candidates.filter { it != mining }.maxWithOrNull(compareBy<BlockPos>(::score).thenByDescending(aimCost))
}

/** Blast centres ranked by how many [targets] lie within [radius] blocks of them. */
internal fun rankBlastCentres(targets: List<BlockPos>, radius: Int) =
    targets.map { c -> c to targets.count { it.distSqr(c) <= radius * radius } }.sortedByDescending { it.second }

/** Gemstone Spread drives gemstones and Mining Spread every other block; a stat missing from the tab list uses its fallback. */
internal fun spreadFor(gemstone: Boolean, miningSpread: Int?, gemstoneSpread: Int?, miningFallback: Int, gemstoneFallback: Int) =
    if (gemstone) gemstoneSpread ?: gemstoneFallback else miningSpread ?: miningFallback

/** Block ids as a palette plus one character per block in BlockPos.betweenClosed order (x fastest, then y, then z), or null if there are too many kinds to encode. */
internal fun encodeGrid(blocks: List<String>): Pair<List<String>, String>? {
    val palette = blocks.distinct()
    if (palette.size > 75) return null
    return palette to blocks.joinToString("") { ('0' + palette.indexOf(it)).toString() }
}
