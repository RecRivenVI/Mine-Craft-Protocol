package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardCharacterInvoker {
    @Invoker("charTyped")
    void minecraftProtocolProbe$character(long window, int codePoint, int modifiers);
}
