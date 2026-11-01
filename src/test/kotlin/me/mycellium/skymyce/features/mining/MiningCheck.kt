package me.mycellium.skymyce.features.mining

import net.minecraft.core.BlockPos

fun main() {
    // A vein along x, plus one ore beside the block being mined.
    val origin = BlockPos(0, 0, 0)
    val ores = setOf(BlockPos(1, 0, 0), BlockPos(2, 0, 0), BlockPos(3, 0, 0), BlockPos(0, 0, 1))
    val nearest = { pos: BlockPos -> pos.distSqr(origin) }
    // 100 spread shares one block between the two neighbours, so the next vein block beyond them is safest and still spreads.
    check(bestSpreadFollowUp(origin, ores, ores::contains, 100.0, nearest) == BlockPos(2, 0, 0))
    // Without spread nothing is taken, so the easiest block to aim at wins.
    check(bestSpreadFollowUp(origin, ores, ores::contains, 0.0, nearest) == BlockPos(1, 0, 0))
    check(bestSpreadFollowUp(origin, emptyList(), ores::contains, 100.0, nearest) == null)
    check(spreadTakeChance(300.0, 2) == 1.0 && spreadTakeChance(100.0, 0) == 0.0 && spreadTakeChance(100.0, 4) == 0.25)

    val cluster = listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), BlockPos(0, 1, 0), BlockPos(10, 0, 0))
    val ranked = rankBlastCentres(cluster, 2)
    check(ranked.first().second == 3 && ranked.first().first != BlockPos(10, 0, 0) && ranked.last() == (BlockPos(10, 0, 0) to 1))

    check(collectorOre("Umber Collector") == "UMBER" && collectorOre("Onyx Gemstone Collector") == "ONYX")
    check(collectorOre("Glacite Walker Slayer") == null)
    check(parseStat(listOf("Stats:", " Mining Speed: ⸕1,234", " Mining Spread: ▚1,050"), "Mining Spread") == 1050)
    check(parseStat(listOf(" Mining Speed: ⸕1,234"), "Mining Spread") == null)
    println("Mining checks passed: spread follow-up, Pickobulus centres, collector commissions and tab stats")
}
