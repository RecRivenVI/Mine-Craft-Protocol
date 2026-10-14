package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSliderButton.class)
public interface SliderValueAccessor {
    @Accessor("value")
    double minecraftProtocolProbe$getValue();

    @Invoker("setValue")
    void minecraftProtocolProbe$setValue(double value);
}
