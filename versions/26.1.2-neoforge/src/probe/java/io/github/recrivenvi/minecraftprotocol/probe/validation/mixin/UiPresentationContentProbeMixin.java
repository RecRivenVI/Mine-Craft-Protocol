package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader-only assertions; no split package or test entry point in the product module. */
@Mixin(
        targets = "io.github.recrivenvi.minecraftprotocol.runtime.UiPresentationDebug",
        remap = false)
public abstract class UiPresentationContentProbeMixin {
    @Unique private static boolean mineCraftProtocol$hudGuardVerified;

    @Shadow
    static UiItemDataGuard.Content content(
            UiItemDataGuard guard, UiItemDataGuard.Budget budget, Component component) {
        throw new AssertionError("Mixin shadow not transformed");
    }

    @Inject(method = "describe", at = @At("HEAD"))
    private static void mineCraftProtocol$verifyNativeContent(CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) mineCraftProtocol$verifyHudContent();
    }

    @Unique
    private static void mineCraftProtocol$verifyHudContent() {
        if (mineCraftProtocol$hudGuardVerified
                || !"conformance-vanilla_ui".equals(System.getProperty("validation.name"))) return;
        mineCraftProtocol$hudGuardVerified = true;
        JsonObject result = new JsonObject();
        result.addProperty("probeOnly", true);
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("target", System.getProperty("validation.target"));
        result.addProperty("testedAt", Instant.now().toString());
        result.addProperty("playerInputUsed", false);
        result.addProperty("worldMutationUsed", false);
        try {
            var guard = new UiItemDataGuard();
            var red =
                    content(
                            guard,
                            new UiItemDataGuard.Budget(),
                            Component.literal("message").withStyle(ChatFormatting.RED));
            var blue =
                    content(
                            guard,
                            new UiItemDataGuard.Budget(),
                            Component.literal("message").withStyle(ChatFormatting.BLUE));
            mineCraftProtocol$require(red.available(), "Native red component must encode");
            mineCraftProtocol$require(blue.available(), "Native blue component must encode");
            mineCraftProtocol$require(
                    !red.fingerprint().equals(blue.fingerprint()),
                    "Native style must change the content guard");
            var missing = content(guard, new UiItemDataGuard.Budget(), null);
            var empty = content(guard, new UiItemDataGuard.Budget(), Component.empty());
            mineCraftProtocol$require(missing.available(), "Null component must encode");
            mineCraftProtocol$require(empty.available(), "Empty component must encode");
            mineCraftProtocol$require(
                    !missing.fingerprint().equals(empty.fingerprint()),
                    "Null and empty are different native values");
            String prefix = "x".repeat(2048);
            var first =
                    content(guard, new UiItemDataGuard.Budget(), Component.literal(prefix + "A"));
            var second =
                    content(guard, new UiItemDataGuard.Budget(), Component.literal(prefix + "B"));
            mineCraftProtocol$require(first.available(), "Full first native text must encode");
            mineCraftProtocol$require(second.available(), "Full second native text must encode");
            mineCraftProtocol$require(
                    !first.fingerprint().equals(second.fingerprint()),
                    "Text beyond the published prefix must remain guarded");
            mineCraftProtocol$require(
                    !content(guard, new UiItemDataGuard.Budget(8), Component.literal(prefix))
                            .available(),
                    "Exhausted budget must fail closed");
            result.addProperty("status", "PASS");
            result.addProperty("checks", 10);
        } catch (Throwable failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("error", failure.toString());
        }
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(
                            System.getProperty("validation.target") + "-hud-content-probe.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("HUD content probe result was not persisted", failure);
        }
    }

    @Unique
    private static void mineCraftProtocol$require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
