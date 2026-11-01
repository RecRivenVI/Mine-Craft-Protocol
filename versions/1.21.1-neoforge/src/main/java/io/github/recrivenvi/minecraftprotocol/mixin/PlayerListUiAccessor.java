package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerTabOverlay.class)
public interface PlayerListUiAccessor {
    @Accessor("visible")
    boolean mineCraftProtocol$visible();

    @Accessor("header")
    Component mineCraftProtocol$header();

    @Accessor("footer")
    Component mineCraftProtocol$footer();

    @Invoker("getPlayerInfos")
    List<PlayerInfo> mineCraftProtocol$displayedPlayers();
}
