package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ObservationRevisionTracker;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.core.ResourceVersionVerifier;
import io.github.recrivenvi.minecraftprotocol.core.UiItemDataGuard;
import io.github.recrivenvi.minecraftprotocol.core.UiToastResetSource;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.SystemToastUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.ToastInstanceUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.ToastManagerUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiToastLifecycleProbeGraphics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.ToLongFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
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

    @Shadow
    static JsonObject canonicalState(
            JsonObject tree,
            Object manager,
            Object instance,
            Toast toast,
            UiItemDataGuard.Content encoded,
            ToLongFunction<Object> identity,
            JsonObject node) {
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
            var kind = new SystemToast.SystemToastId(60000);
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
            var generation = (UiToastResetSource) red;
            Minecraft client = Minecraft.getInstance();
            var nativeManager = new ToastComponent(client);
            nativeManager.addToast(red);
            var graphics = new UiToastLifecycleProbeGraphics(client);
            nativeManager.render(graphics);
            Object nativeInstance =
                    ((ToastManagerUiAccessor) nativeManager)
                            .minecraftProtocolProbe$visible().stream()
                                    .filter(
                                            instance ->
                                                    ((ToastInstanceUiAccessor) instance)
                                                                    .minecraftProtocolProbe$toast()
                                                            == red)
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new AssertionError(
                                                            "Probe-owned native Toast instance missing"));
            long beforeReset = generation.minecraftProtocolProbe$resetGeneration();
            red.reset(Component.literal("reset generation"), Component.literal("same message"));
            var tracker = new ObservationRevisionTracker();
            JsonObject resetState =
                    minecraftProtocolProbe$resetState(guard, nativeManager, nativeInstance, red);
            JsonObject receipt = tracker.revision("ui_node", "probe_owned_toast", resetState);
            var nativeFields = (SystemToastUiAccessor) red;
            boolean pending = nativeFields.minecraftProtocolProbe$changed();
            red.render(graphics, nativeManager, 1234L);
            JsonObject consumedState =
                    minecraftProtocolProbe$resetState(guard, nativeManager, nativeInstance, red);
            JsonObject consumed = tracker.revision("ui_node", "probe_owned_toast", consumedState);
            minecraftProtocolProbe$require(
                    generation.minecraftProtocolProbe$resetGeneration() == beforeReset + 1
                            && pending
                            && !nativeFields.minecraftProtocolProbe$changed()
                            && nativeFields.minecraftProtocolProbe$lastChanged() == 1234L
                            && receipt.equals(consumed),
                    "One passive reset generation must survive actual native lifetime consumption");
            result.addProperty("nativeTickVersionStable", true);
            red.reset(Component.literal("reset generation"), Component.literal("same message"));
            JsonObject sameValueState =
                    minecraftProtocolProbe$resetState(guard, nativeManager, nativeInstance, red);
            JsonObject sameValue = tracker.revision("ui_node", "probe_owned_toast", sameValueState);
            boolean staleRejected = false;
            try {
                ResourceVersionVerifier.verify(receipt, sameValue);
            } catch (ProtocolState.ProtocolException failure) {
                staleRejected = "STALE_RESOURCE_REVISION".equals(failure.code());
            }
            minecraftProtocolProbe$require(
                    generation.minecraftProtocolProbe$resetGeneration() == beforeReset + 2
                            && resetState.get("content").equals(sameValueState.get("content"))
                            && !receipt.equals(sameValue)
                            && staleRejected,
                    "External same-value native reset must invalidate the confirmed prior token");
            result.addProperty("sameValueResetInvalidates", true);
            nativeManager.clear();
            result.addProperty("status", "PASS");
            result.addProperty("checks", 9);
            result.addProperty("resetGenerationChecks", 2);
            result.addProperty(
                    "resetConsumption", "native_render_on_probe_owned_toast_no_pixel_canvas");
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
    private static JsonObject minecraftProtocolProbe$resetState(
            UiItemDataGuard guard, ToastComponent manager, Object instance, SystemToast toast) {
        JsonObject tree = new JsonObject();
        var window = Minecraft.getInstance().getWindow();
        tree.addProperty("screenIdentity", 0);
        tree.addProperty("overlayIdentity", 0);
        tree.addProperty("width", window.getGuiScaledWidth());
        tree.addProperty("height", window.getGuiScaledHeight());
        tree.addProperty("guiScale", window.getGuiScale());
        return canonicalState(
                tree,
                manager,
                instance,
                toast,
                content(guard, new UiItemDataGuard.Budget(), toast),
                System::identityHashCode,
                new JsonObject());
    }

    @Unique
    private static void minecraftProtocolProbe$require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
