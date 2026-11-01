package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.client.ExtensionPacksScreen;
import io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackSelectionScreen.class)
public abstract class ExtensionPackFolderMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private static net.minecraft.network.chat.Component DRAG_AND_DROP;

    @Inject(method = "lambda$init$0", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$folder(Button button, CallbackInfo ci) {
        if ((Object) this instanceof ExtensionPacksScreen && !AgentInputContext.isNativeTask())
            ci.cancel();
    }

    @Redirect(
            method = "init",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/gui/screens/packs/PackSelectionScreen;DRAG_AND_DROP:Lnet/minecraft/network/chat/Component;"))
    private net.minecraft.network.chat.Component mineCraftProtocol$hint() {
        return (Object) this instanceof ExtensionPacksScreen
                ? net.minecraft.network.chat.Component.literal("启用状态将在重启后生效")
                : DRAG_AND_DROP;
    }
}
