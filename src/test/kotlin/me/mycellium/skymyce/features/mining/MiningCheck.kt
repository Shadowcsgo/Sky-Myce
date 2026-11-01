package me.mycellium.skymyce.features.mining

import net.minecraft.core.BlockPos

fun main() {
    // A vein along x, plus one ore beside the block being mined.
    val origin = BlockPos(0, 0, 0)
    val ores = setOf(BlockPos(1, 0, 0), BlockPos(2, 0, 0), BlockPos(3, 0, 0), BlockPos(0, 0, 1))
    val nearest = { pos: BlockPos -> pos.distSqr(origin) }
    val air = { pos: BlockPos -> pos !in ores }
    // The two blocks around the mined one risk the spread, so the next vein block beyond them is safest and still spreads.
    check(bestSpreadFollowUp(origin, ores, ores::contains, air, 288.0, false, nearest) == BlockPos(2, 0, 0))
    // Without spread nothing is taken, so the easiest block to aim at wins.
    check(bestSpreadFollowUp(origin, ores, ores::contains, air, 0.0, false, nearest) == BlockPos(1, 0, 0))
    check(bestSpreadFollowUp(origin, emptyList(), ores::contains, air, 288.0, false, nearest) == null)
    // Once the spread has landed, what is left is safe, so the nearest neighbour with spread of its own wins.
    check(bestSpreadFollowUp(origin, ores, ores::contains, air, 288.0, true, nearest) == BlockPos(1, 0, 0))
    // Fitted rate: ~22% each among nine candidates at 288 spread.
    check(kotlin.math.abs(spreadTakeChance(288.0, 9.0) - 0.2198) < 0.001)
    check(spreadTakeChance(1000.0, 1.0) == 1.0 && spreadTakeChance(288.0, 0.0) == 0.0 && spreadTakeChance(0.0, 5.0) == 0.0)

    // Spread takes exposed same-family blocks from all 26 around (edges too), never buried ones or other kinds such as stone.
    val vein = setOf(BlockPos(1, 0, 0), BlockPos(1, 1, 0), BlockPos(0, 1, 1))
    val stone = BlockPos.betweenClosed(-2, -2, -2, 2, 2, 2).map { it.immutable() }.filter { it != origin && it !in vein }.toSet()
    val solid = { pos: BlockPos -> pos in vein || pos in stone }
    // (1,0,0) touches the mined block; (1,1,0) and (0,1,1) are edge neighbours sealed in by stone.
    check(spreadPool(origin, setOf(origin), vein::contains) { !solid(it) } == listOf(BlockPos(1, 0, 0)))
    val openEdge = spreadPool(origin, setOf(origin), vein::contains) { !solid(it) || it == BlockPos(0, 2, 1) }
    check(openEdge.toSet() == setOf(BlockPos(1, 0, 0), BlockPos(0, 1, 1)))

    // Suggestions must be visible: a wall in the way hides a block unless it is the one about to break.
    val eye = net.minecraft.world.phys.Vec3(0.5, 1.6, -2.5)
    val target = BlockPos(0, 1, 0)
    val wall = BlockPos(0, 1, -1)
    check(canSee(eye, target) { it != target })
    check(!canSee(eye, target) { it != target && it != wall })
    val mining = wall
    check(canSee(eye, target) { it == mining || (it != target && it != wall) })
    // Only open toward +z, away from the eye (e.g. bedrock and stone on every other side).
    val sealed = setOf(target, wall, BlockPos(1, 1, 0), BlockPos(-1, 1, 0), BlockPos(0, 2, 0), BlockPos(0, 0, 0))
    check(!canSee(eye, target) { it !in sealed })

    val cluster = listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), BlockPos(0, 1, 0), BlockPos(10, 0, 0))
    val ranked = rankBlastCentres(cluster, 2)
    check(ranked.first().second == 3 && ranked.first().first != BlockPos(10, 0, 0) && ranked.last() == (BlockPos(10, 0, 0) to 1))

    check(collectorOre("Umber Collector") == "UMBER" && collectorOre("Onyx Gemstone Collector") == "ONYX")
    check(collectorOre("Glacite Walker Slayer") == null)
    check(parseStat(listOf("Stats:", " Mining Speed: ⸕1,234", " Mining Spread: ▚1,050"), "Mining Spread") == 1050)
    check(parseStat(listOf(" Mining Speed: ⸕1,234"), "Mining Spread") == null)
    check(spreadFor(true, 300, 50, 100, 100) == 50 && spreadFor(false, 300, 50, 100, 100) == 300)
    check(spreadFor(true, 300, null, 100, 70) == 70 && spreadFor(false, null, 50, 120, 70) == 120)

    val (palette, grid) = encodeGrid(listOf("minecraft:stone", "minecraft:packed_ice", "minecraft:stone"))!!
    check(palette == listOf("minecraft:stone", "minecraft:packed_ice") && grid == "010")
    check(encodeGrid(List(76) { "b$it" }) == null)
    println("Mining checks passed: spread follow-up, Pickobulus centres, collector commissions, tab stats, gemstone spread and sample encoding")
}
