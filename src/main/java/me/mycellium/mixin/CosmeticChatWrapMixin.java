package me.mycellium.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.mycellium.skymyce.features.social.CosmeticNames;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;

/** Chat wraps through the splitter directly, not Font.split: measure displayed cosmetic names here too, as Font.split does. */
@Mixin(ComponentRenderUtils.class)
public abstract class CosmeticChatWrapMixin {
    @WrapMethod(method = "wrapComponents")
    private static List<FormattedCharSequence> skymyce$wrap(FormattedText text, int width, Font font, Operation<List<FormattedCharSequence>> original) {
        if (!CosmeticNames.active()) return original.call(text, width, font);
        FormattedText displayed = CosmeticNames.display(text);
        CosmeticNames.enterOriginal();
        try { return original.call(displayed, width, font).stream().map(CosmeticNames::original).toList(); }
        finally { CosmeticNames.exitOriginal(); }
    }
}
