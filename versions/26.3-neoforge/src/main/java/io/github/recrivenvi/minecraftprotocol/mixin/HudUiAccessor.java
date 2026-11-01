package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudUiAccessor {
    @Accessor("title")
    Component mineCraftProtocol$title();

    @Accessor("subtitle")
    Component mineCraftProtocol$subtitle();

    @Accessor("titleTime")
    int mineCraftProtocol$titleTime();

    @Accessor("titleFadeInTime")
    int mineCraftProtocol$titleFadeInTime();

    @Accessor("titleStayTime")
    int mineCraftProtocol$titleStayTime();

    @Accessor("titleFadeOutTime")
    int mineCraftProtocol$titleFadeOutTime();

    @Accessor("overlayMessageString")
    Component mineCraftProtocol$actionbar();

    @Accessor("overlayMessageTime")
    int mineCraftProtocol$actionbarTime();
}
