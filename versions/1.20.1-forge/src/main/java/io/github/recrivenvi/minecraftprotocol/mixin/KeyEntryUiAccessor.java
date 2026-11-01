package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryUiAccessor {
    @Accessor("key")
    KeyMapping mineCraftProtocol$key();

    @Accessor("name")
    Component mineCraftProtocol$name();

    @Accessor("changeButton")
    Button mineCraftProtocol$changeButton();

    @Accessor("resetButton")
    Button mineCraftProtocol$resetButton();
}
