package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconPowerButton")
public interface BeaconPowerUiAccessor {
    @Accessor("effect")
    Holder<MobEffect> mineCraftProtocol$effect();

    @Accessor("isPrimary")
    boolean mineCraftProtocol$primary();

    @Accessor("tier")
    int mineCraftProtocol$tier();
}
