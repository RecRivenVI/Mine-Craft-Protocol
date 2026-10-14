package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface SignUiAccessor {
    @Accessor("line")
    int minecraftProtocolProbe$line();

    @Accessor("messages")
    String[] minecraftProtocolProbe$messages();

    @Accessor("sign")
    SignBlockEntity minecraftProtocolProbe$sign();

    @Invoker("isValid")
    boolean minecraftProtocolProbe$valid();
}
