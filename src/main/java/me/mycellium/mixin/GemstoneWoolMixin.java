package me.mycellium.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Vanilla and Sodium chunk meshing both get block models here, so swapping the model changes only the look. */
@Mixin(BlockStateModelSet.class)
public class GemstoneWoolMixin {
    /** Lives on the model set, so a resource reload starts fresh and a pane's model stays the same object between lookups. */
    @Unique private final Map<BlockState, BlockStateModel> skymyce$woolPanes = new ConcurrentHashMap<>();

    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private BlockStateModel skymyce$gemstoneWool(BlockStateModel original, BlockState state) {
        return me.mycellium.skymyce.features.mining.MiningFeatures.INSTANCE.gemstoneModel(state, original, (BlockStateModelSet) (Object) this, skymyce$woolPanes);
    }
}
