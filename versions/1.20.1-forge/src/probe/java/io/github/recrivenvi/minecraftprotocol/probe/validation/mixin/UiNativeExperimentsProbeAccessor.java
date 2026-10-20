package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ExperimentsScreen.class)
public interface UiNativeExperimentsProbeAccessor {
    @Invoker("<init>")
    static ExperimentsScreen minecraftProtocolProbe$create(
            Screen previous, PackRepository packs, Consumer<PackRepository> output) {
        throw new AssertionError();
    }
}
