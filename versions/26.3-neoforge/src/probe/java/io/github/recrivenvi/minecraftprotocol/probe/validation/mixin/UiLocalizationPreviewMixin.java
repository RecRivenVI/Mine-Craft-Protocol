package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explicit host-requested final-frame previews; never part of Runtime evidence capture. */
@Mixin(Minecraft.class)
public abstract class UiLocalizationPreviewMixin {
    @Unique private long mineCraftProtocol$nextPreview;
    @Unique private String mineCraftProtocol$previewNonce = "";

    @Inject(
            method = "renderFrame",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V",
                            shift = At.Shift.AFTER))
    private void mineCraftProtocol$localizedPreview(boolean advanceTime, CallbackInfo ci) {
        if (!Boolean.getBoolean("mine_craft_protocol.localizationPreview")) return;
        long now = System.nanoTime();
        if (now < mineCraftProtocol$nextPreview) return;
        mineCraftProtocol$nextPreview = now + 250_000_000L;
        Minecraft client = (Minecraft) (Object) this;
        Path root = client.gameDirectory.toPath().resolve("mine_craft_protocol/locale-preview");
        Path request = root.resolve("request.json");
        if (!Files.isRegularFile(request)) return;
        try {
            var value = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
            String nonce = value.get("nonce").getAsString();
            String name = value.get("name").getAsString();
            if (nonce.equals(mineCraftProtocol$previewNonce)
                    || !name.matches("(?:en_us|zh_cn)-(?:READ|OPERATE|TAKEOVER)")) return;
            mineCraftProtocol$previewNonce = nonce;
            var captions = new com.google.gson.JsonObject();
            if (client.gui.screen() != null) {
                for (var child : client.gui.screen().children()) {
                    if (child
                            instanceof
                            net.minecraft.client.gui.screens.packs.TransferableSelectionList list) {
                        for (var row : list.children()) {
                            if (!(row
                                    instanceof
                                    io.github.recrivenvi.minecraftprotocol.mixin
                                            .PackEntryUiAccessor)) continue;
                            var pack =
                                    ((io.github.recrivenvi.minecraftprotocol.mixin
                                                            .PackEntryUiAccessor)
                                                    row)
                                            .mineCraftProtocol$pack();
                            captions.addProperty(pack.getId(), pack.getDescription().getString());
                        }
                    }
                }
            }
            value.add("captions", captions);
            Screenshot.takeScreenshot(
                    client.gameRenderer.mainRenderTarget(),
                    image ->
                            net.minecraft.util.Util.ioPool()
                                    .execute(
                                            () -> {
                                                try (image) {
                                                    image.writeToFile(root.resolve(name + ".png"));
                                                    Files.writeString(
                                                            root.resolve(name + ".json"),
                                                            value.toString());
                                                } catch (Exception error) {
                                                    throw new IllegalStateException(
                                                            "Localization preview failed", error);
                                                }
                                            }));
        } catch (java.io.IOException | com.google.gson.JsonParseException incomplete) {
            // A host write can overlap this read; the next frame retries the incomplete request.
        }
    }
}
