package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardCharacterInvoker {
    @Invoker("charTyped")
    void mineCraftProtocol$character(long window, int codePoint, int modifiers);
}
