package io.github.recrivenvi.extensions.modernui.nativebridge.mixin;

import io.github.recrivenvi.extensions.modernui.nativebridge.FrameworkTasks;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "icyllis.arc3d.opengl.GLGraphicsPipelineBuilder", remap = false)
public abstract class CompilerMixin {
    @Redirect(
            method = "createGraphicsPipeline",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<?> nativeCompilerOwner(Supplier<?> work) {
        return FrameworkTasks.compile(work);
    }
}
