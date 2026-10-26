package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryUiAccessor {
    @Accessor("key")
    KeyMapping minecraftProtocolProbe$key();

    @Accessor("name")
    Component minecraftProtocolProbe$name();

    @Accessor("changeButton")
    Button minecraftProtocolProbe$changeButton();

    @Accessor("resetButton")
    Button minecraftProtocolProbe$resetButton();
}
