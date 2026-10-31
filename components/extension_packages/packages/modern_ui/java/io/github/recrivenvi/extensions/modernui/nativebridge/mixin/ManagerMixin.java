package io.github.recrivenvi.extensions.modernui.nativebridge.mixin;

import io.github.recrivenvi.extensions.modernui.nativebridge.ModernBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "icyllis.modernui.mc.UIManager", remap = false)
public abstract class ManagerMixin {
    @Inject(
            method = {
                "onPostMouseInput",
                "onHoverMove",
                "onScroll",
                "onKeyPress",
                "onKeyRelease",
                "onPreKeyInput"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void suppressUnownedInput(CallbackInfo callback) {
        if (ModernBridge.rejectUnownedInput()) callback.cancel();
    }

    @Inject(method = "onCharTyped", at = @At("HEAD"), cancellable = true, require = 1)
    private void suppressUnownedText(CallbackInfoReturnable<Boolean> callback) {
        if (ModernBridge.rejectUnownedInput()) callback.setReturnValue(false);
    }

    @Redirect(
            method = {"onPostMouseInput", "onHoverMove", "onScroll", "onKeyPress", "onKeyRelease"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Licyllis/modernui/mc/UIManager$ViewRootImpl;enqueueInputEvent(Licyllis/modernui/view/InputEvent;)V"))
    private void logicalQueue(@Coerce Object root, @Coerce Object event) {
        ModernBridge.queue(root, event);
    }

    @Redirect(
            method = "onPostMouseInput",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetMouseButton(JI)I"))
    private int logicalButton(long window, int button) {
        return ModernBridge.mouseButton(window, button);
    }

    @Redirect(
            method = "onCharTyped",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Licyllis/modernui/core/Core;postOnMainThread(Ljava/lang/Runnable;)V"))
    private void logicalCharacterMain(Runnable action) {
        ModernBridge.postMain(action);
    }

    @ModifyArg(
            method = "commitCharInput",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Licyllis/modernui/core/Message;obtain(Licyllis/modernui/core/Handler;Ljava/lang/Runnable;)Licyllis/modernui/core/Message;"),
            index = 1)
    private Runnable logicalCharacterDelivery(Runnable action) {
        return ModernBridge.textDelivery(action);
    }
}
