package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSliderButton.class)
public interface SliderValueAccessor {
    @Accessor("value")
    double mineCraftProtocol$getValue();

    @Invoker("setValue")
    void mineCraftProtocol$setValue(double value);
}
