package me.mycellium.skymyce.config.mining

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.CategoryKt
import me.mycellium.skymyce.config.Config

object MiningConfig : CategoryKt("Mining") {
    val crystalHollowsInfo by boolean(true) {
        name = TranslatableValue.literal("Crystal Hollows Info")
        description = TranslatableValue.literal("Displays useful Crystal Hollows info in your lobby")
    }

    val balNotify by boolean(false) {
        name = TranslatableValue.literal("Bal Notifier")
        description = TranslatableValue.literal("Notifies bal HP in chat §c(Only works while in a party)")
    }

    val autoNucleusWarp by boolean(false) {
        name = TranslatableValue.literal("Auto Nucleus Warp")
        description = TranslatableValue.literal("Automatically types \"/warp nuc\" when obtaining a crystal in the crystal hollows")
    }

    val autoNucleusWarpMin by double(0.3) {
        name = TranslatableValue.literal("Warp Min")

        slider = true
        range = 0.0..1.0
    }

    val autoNucleusWarpMax by double(0.7) {
        name = TranslatableValue.literal("Warp Max")

        slider = true
        range = 0.0..1.0
    }

    val commissionHighlight by boolean(false) {
        name = TranslatableValue.literal("Commission Highlight")
        description = TranslatableValue.literal("Highlights what your open commissions need: the ore or gemstone blocks around you, and the mobs for slayer commissions, including Glacite mobs in Mineshafts")
    }

    val gemstoneWool by boolean(false) {
        name = TranslatableValue.literal("Wool Gemstones")
        description = TranslatableValue.literal("Draws gemstone glass and panes with solid wool of the same color in the Crystal Hollows, Dwarven Mines and Mineshafts, so they are easier to see. Panes stay thin and only the look changes")
    }

    val spreadPredictor by boolean(false) {
        name = TranslatableValue.literal("Mining Spread Predictor")
        description = TranslatableValue.literal("After you break an ore or gemstone with a pickaxe or drill, flashes the blocks your Mining Spread took and outlines the best block to mine next. Taps that don't break a block, stone and Hard Stone are ignored; off in the Catacombs")
    }

    val spreadFallback by int(100) {
        name = TranslatableValue.literal("Mining Spread (if hidden)")
        description = TranslatableValue.literal("Used for non-gemstone blocks when your tab list Stats widget does not show Mining Spread")

        slider = true
        range = 0..1000
    }

    val gemstoneSpreadFallback by int(100) {
        name = TranslatableValue.literal("Gemstone Spread (if hidden)")
        description = TranslatableValue.literal("Used for gemstones when your tab list Stats widget does not show Gemstone Spread")

        slider = true
        range = 0..1000
    }

    val recordSpread by boolean(false) {
        name = TranslatableValue.literal("Record Spread Samples")
        description = TranslatableValue.literal("Developer: saves each ore you break, its 7x7x7 surroundings and which blocks the spread took to config/skymyce/mining_spread_samples.json")
        condition = { Config.devMode }
    }

    val pickobulusHelper by boolean(false) {
        name = TranslatableValue.literal("Pickobulus Commission Spot")
        description = TranslatableValue.literal("While you hold a tool with Pickobulus, scans the veins all around you for your open block commissions and marks where a blast breaks the most. When that spot is hidden or far an arrow leads to it, and when it is off screen the Pickobulus Direction widget points the way")
    }

    val pickobulusColor by color(0xFFAA00) {
        name = TranslatableValue.literal("Pickobulus Color")
        description = TranslatableValue.literal("Color of the Pickobulus marker, blocks, arrow and label")
        allowAlpha = false
    }

    val pickobulusRadius by int(3) {
        name = TranslatableValue.literal("Pickobulus Radius")
        description = TranslatableValue.literal("Blast radius in blocks, including perk or Sky Mall boosts")

        slider = true
        range = 1..5
    }
}