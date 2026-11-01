package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TutorialToast.class)
public interface TutorialToastUiAccessor {
    @Accessor("progressable")
    boolean mineCraftProtocol$progressable();

    @Accessor("progress")
    float mineCraftProtocol$progress();

    @Accessor("visibility")
    net.minecraft.client.gui.components.toasts.Toast.Visibility mineCraftProtocol$visibility();

    @Accessor("title")
    Component mineCraftProtocol$title();

    @Accessor("message")
    Component mineCraftProtocol$message();
}
