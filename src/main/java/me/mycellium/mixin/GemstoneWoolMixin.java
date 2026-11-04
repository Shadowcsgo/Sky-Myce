package me.mycellium.mixin;

import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla and Sodium chunk meshing both get block models here, so swapping the state changes only the look. */
@Mixin(BlockStateModelSet.class)
public class GemstoneWoolMixin {
    @ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
    private BlockState skymyce$gemstoneWool(BlockState state) {
        return me.mycellium.skymyce.features.mining.MiningFeatures.INSTANCE.gemstoneLook(state);
    }
}
