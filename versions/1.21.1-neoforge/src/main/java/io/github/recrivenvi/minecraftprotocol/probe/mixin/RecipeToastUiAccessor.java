package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeToast.class)
public interface RecipeToastUiAccessor {
    @Accessor("recipes")
    List<?> minecraftProtocolProbe$recipes();
}
