package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementToast.class)
public interface AdvancementToastUiAccessor {
    @Accessor("advancement")
    net.minecraft.advancements.AdvancementHolder minecraftProtocolProbe$advancement();
}
