package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconConfirmButton")
public interface BeaconConfirmUiMarker {}
