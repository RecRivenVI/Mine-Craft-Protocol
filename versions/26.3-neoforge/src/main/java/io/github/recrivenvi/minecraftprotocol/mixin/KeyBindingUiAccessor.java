package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface KeyBindingUiAccessor {
    @Accessor("key")
    InputConstants.Key mineCraftProtocol$key();

    @Accessor("clickCount")
    int mineCraftProtocol$pendingClicks();

    @Invoker("release")
    void mineCraftProtocol$release();
}
