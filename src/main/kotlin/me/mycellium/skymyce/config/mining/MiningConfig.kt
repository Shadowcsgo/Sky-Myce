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

    val spreadPredictor by boolean(false) {
        name = TranslatableValue.literal("Mining Spread Predictor")
        description = TranslatableValue.literal("While mining an ore, outlines the block to mine next: one your Mining Spread probably won't take, with the most same-type neighbours for the next spread")
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
        description = TranslatableValue.literal("In the Glacite Tunnels, marks the visible block where a Pickobulus blast breaks the most blocks for your open collector commissions")
    }

    val pickobulusRadius by int(3) {
        name = TranslatableValue.literal("Pickobulus Radius")
        description = TranslatableValue.literal("Blast radius in blocks, including perk or Sky Mall boosts")

        slider = true
        range = 1..5
    }
}