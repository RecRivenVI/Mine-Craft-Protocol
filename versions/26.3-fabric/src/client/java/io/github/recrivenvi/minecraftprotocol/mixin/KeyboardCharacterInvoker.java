package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardCharacterInvoker {
    @Invoker("charTyped")
    void mineCraftProtocol$character(long window, CharacterEvent event);
}
