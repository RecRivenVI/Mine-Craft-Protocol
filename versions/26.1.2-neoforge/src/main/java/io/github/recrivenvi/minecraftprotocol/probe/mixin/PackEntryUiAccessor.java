package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.packs.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TransferableSelectionList.PackEntry.class)
public interface PackEntryUiAccessor {
    @Accessor("pack")
    PackSelectionModel.Entry minecraftProtocolProbe$pack();
}
