package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconPowerButton")
public interface BeaconPowerUiAccessor {
    @Accessor("effect")
    MobEffect minecraftProtocolProbe$effect();

    @Accessor("isPrimary")
    boolean minecraftProtocolProbe$primary();

    @Accessor("tier")
    int minecraftProtocolProbe$tier();
}
