package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.WorldPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Observe native presentation without scanning or publishing hidden state. */
public final class WorldPresentationSnapshot {
    private static final WorldPresentation SAMPLE = new WorldPresentation();

    private WorldPresentationSnapshot() {}

    private static WorldPresentation.Context context() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.getCameraEntity() == null) return null;
        var camera = client.getCameraEntity();
        Object target =
                client.hitResult instanceof BlockHitResult block
                                && block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                        ? block.getBlockPos()
                        : null;
        boolean active =
                client.screen == null
                        && client.getOverlay() == null
                        && !client.options.hideGui
                        && client.options.getCameraType().isFirstPerson()
                        && client.getWindow().getWidth() > 0
                        && client.getWindow().getHeight() > 0;
        String view =
                camera.getX()
                        + ":"
                        + camera.getY()
                        + ":"
                        + camera.getZ()
                        + ":"
                        + camera.getYRot()
                        + ":"
                        + camera.getXRot()
                        + ":"
                        + client.options.fov().get();
        return new WorldPresentation.Context(
                client.level,
                camera,
                client.screen,
                target,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                active,
                view);
    }

    public static void begin() {
        SAMPLE.begin(context());
    }

    public static void outlineSubmitted(Object target) {
        SAMPLE.outlineSubmitted(target);
    }

    public static void finish() {
        SAMPLE.finish(context(), System.nanoTime());
    }

    static JsonObject capture() {
        return SAMPLE.read(context(), System.nanoTime());
    }
}
