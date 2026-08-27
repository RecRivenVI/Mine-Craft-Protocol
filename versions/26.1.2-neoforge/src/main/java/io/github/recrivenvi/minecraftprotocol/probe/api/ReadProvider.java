package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit opt-in read provider for detached LIVE Minecraft-domain data.
 * Implementations own their thread scheduling and must not return live Minecraft objects.
 */
public interface ReadProvider {
    String id();

    String perspective();

    String threadAffinity();

    CompletableFuture<JsonObject> read(JsonObject query);
}

