package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.client.gui.screens.options.UnsupportedGraphicsWarningScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Probe-only construction of the actual native warning page, without creating a GPU failure. */
@Mixin(UnsupportedGraphicsWarningScreen.class)
public interface UiRecoveryGraphicsProbeInvoker {
    @Invoker("<init>")
    static UnsupportedGraphicsWarningScreen minecraftProtocolProbe$create(
            Component title,
            List<Component> message,
            ImmutableList<UnsupportedGraphicsWarningScreen.ButtonOption> buttons) {
        throw new AssertionError("Probe constructor invoker was not transformed");
    }
}
