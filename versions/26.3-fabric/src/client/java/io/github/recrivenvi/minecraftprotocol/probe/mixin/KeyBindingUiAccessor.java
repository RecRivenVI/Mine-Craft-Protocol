package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface KeyBindingUiAccessor {
    @Accessor("key")
    InputConstants.Key minecraftProtocolProbe$key();

    @Accessor("clickCount")
    int minecraftProtocolProbe$pendingClicks();

    @Invoker("release")
    void minecraftProtocolProbe$release();
}
