package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameModeSwitcherScreen.class)
public interface GameModeSwitcherUiAccessor {
    @Accessor("slots")
    List<GameModeSwitcherScreen.GameModeSlot> minecraftProtocolProbe$slots();
}
