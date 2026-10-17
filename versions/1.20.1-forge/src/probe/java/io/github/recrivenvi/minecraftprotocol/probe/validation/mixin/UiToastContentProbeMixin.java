package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs against the actual transformed production encoder and native Toast classes. */
@Mixin(targets = "io.github.recrivenvi.minecraftprotocol.probe.runtime.UiToastDebug", remap = false)
public abstract class UiToastContentProbeMixin {
    @Unique private static boolean minecraftProtocolProbe$verified;

    @Shadow
    static UiItemDataGuard.Content content(
            UiItemDataGuard guard, UiItemDataGuard.Budget budget, Toast toast) {
        throw new AssertionError("Mixin shadow not transformed");
    }

    @Inject(method = "describe", at = @At("HEAD"))
    private static void minecraftProtocolProbe$verify(CallbackInfo callback) {
        if (minecraftProtocolProbe$verified
                || !"conformance-vanilla_ui".equals(System.getProperty("validation.name"))) return;
        minecraftProtocolProbe$verified = true;
        JsonObject result = new JsonObject();
        result.addProperty("probeOnly", true);
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("target", System.getProperty("validation.target"));
        result.addProperty("testedAt", Instant.now().toString());
        result.addProperty("playerInputUsed", false);
        result.addProperty("worldMutationUsed", false);
        try {
            var guard = new UiItemDataGuard();
            var kind = SystemToast.SystemToastIds.PERIODIC_NOTIFICATION;
            var red =
                    new SystemToast(
                            kind,
                            Component.literal("same").withStyle(ChatFormatting.RED),
                            Component.literal("message"));
            var blue =
                    new SystemToast(
                            kind,
                            Component.literal("same").withStyle(ChatFormatting.BLUE),
                            Component.literal("message"));
            var a = content(guard, new UiItemDataGuard.Budget(), red);
            var b = content(guard, new UiItemDataGuard.Budget(), blue);
            minecraftProtocolProbe$require(a.available(), "Red native content must encode");
            minecraftProtocolProbe$require(b.available(), "Blue native content must encode");
            minecraftProtocolProbe$require(
                    !a.fingerprint().equals(b.fingerprint()), "Native style must be guarded");
            String prefix = "x".repeat(2048);
            var first =
                    new SystemToast(
                            kind, Component.literal("title"), Component.literal(prefix + "A"));
            var second =
                    new SystemToast(
                            kind, Component.literal("title"), Component.literal(prefix + "B"));
            a = content(guard, new UiItemDataGuard.Budget(), first);
            b = content(guard, new UiItemDataGuard.Budget(), second);
            minecraftProtocolProbe$require(a.available(), "Full first content must encode");
            minecraftProtocolProbe$require(b.available(), "Full second content must encode");
            minecraftProtocolProbe$require(
                    !a.fingerprint().equals(b.fingerprint()),
                    "Unpublished native tail must be guarded");
            minecraftProtocolProbe$require(
                    !content(guard, new UiItemDataGuard.Budget(8), first).available(),
                    "Capture budget must fail closed");
            result.addProperty("status", "PASS");
            result.addProperty("checks", 7);
        } catch (Throwable failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("error", failure.toString());
        }
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(
                            System.getProperty("validation.target") + "-toast-content-probe.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(
                    "Toast content probe result was not persisted", failure);
        }
    }

    @Unique
    private static void minecraftProtocolProbe$require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
