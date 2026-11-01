package io.github.recrivenvi.minecraftprotocol.extensions.exposure.client;

import static io.github.recrivenvi.minecraftprotocol.extensions.exposure.bridge.NativeAccess.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.api.extension.*;
import io.github.recrivenvi.minecraftprotocol.extensions.exposure.bridge.ReadProvider;
import java.util.*;

/**
 * Reads actual held-camera/film components, never creates IDs or invokes a shutter/attachment
 * setter.
 */
public final class ExposureClientExtension implements Extension {
    public void initialize(ExtensionContext context) {
        if (context.side() != ExtensionContext.Side.CLIENT) return;
        ReadProvider.register(
                context,
                "camera",
                "client_thread",
                query -> query.size() == 0,
                query -> camera(context));
    }

    private static JsonObject camera(ExtensionContext context) {
        var result = new JsonObject();
        Object client = context.nativeContext("client_thread"), player = field(client, "player");
        result.addProperty("available", player != null);
        if (player == null) return result;
        Object stack = call(player, "getMainHandItem"), item = call(stack, "getItem");
        result.addProperty("nativeType", item.getClass().getName());
        boolean isCamera =
                item.getClass()
                        .getName()
                        .equals("io.github.mortuusars.exposure.world.item.camera.CameraItem");
        result.addProperty("camera", isCamera);
        if (!isCamera) return result;
        result.addProperty("active", Boolean.TRUE.equals(call(item, "isActive", stack)));
        Object attachment = call(item, "getFilmAttachment");
        boolean present = Boolean.TRUE.equals(call(attachment, "isPresent", stack));
        result.addProperty("filmPresent", present);
        if (present) {
            Object stored = call(attachment, "get", stack),
                    film = call(stored, "getForReading"),
                    filmItem = call(film, "getItem");
            List<?> frames = (List<?>) call(filmItem, "getStoredFrames", film);
            result.addProperty("frameCount", frames.size());
            var captured = new JsonArray();
            for (Object frame : frames) {
                if (captured.size() >= 64) {
                    result.addProperty("framesTruncated", true);
                    break;
                }
                var data = new JsonObject();
                data.addProperty("identifier", Objects.toString(call(frame, "identifier")));
                captured.add(data);
            }
            result.add("frames", captured);
        }
        return result;
    }
}
