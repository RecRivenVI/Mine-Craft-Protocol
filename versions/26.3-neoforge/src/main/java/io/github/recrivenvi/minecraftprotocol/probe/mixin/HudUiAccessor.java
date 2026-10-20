package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudUiAccessor {
    @Accessor("title")
    Component minecraftProtocolProbe$title();

    @Accessor("subtitle")
    Component minecraftProtocolProbe$subtitle();

    @Accessor("titleTime")
    int minecraftProtocolProbe$titleTime();

    @Accessor("titleFadeInTime")
    int minecraftProtocolProbe$titleFadeInTime();

    @Accessor("titleStayTime")
    int minecraftProtocolProbe$titleStayTime();

    @Accessor("titleFadeOutTime")
    int minecraftProtocolProbe$titleFadeOutTime();

    @Accessor("overlayMessageString")
    Component minecraftProtocolProbe$actionbar();

    @Accessor("overlayMessageTime")
    int minecraftProtocolProbe$actionbarTime();
}
