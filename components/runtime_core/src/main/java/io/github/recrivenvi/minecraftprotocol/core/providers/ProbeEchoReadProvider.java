package io.github.recrivenvi.minecraftprotocol.core.providers;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.ReadProvider;
import java.util.concurrent.CompletableFuture;

public final class ProbeEchoReadProvider implements ReadProvider {
    private final String modId;

    public ProbeEchoReadProvider(String modId) {
        this.modId = java.util.Objects.requireNonNull(modId);
    }

    @Override
    public String id() {
        return this.modId + ":echo";
    }

    @Override
    public String perspective() {
        return "provider_declared_live";
    }

    @Override
    public String threadAffinity() {
        return "provider_managed";
    }

    @Override
    public CompletableFuture<JsonObject> read(JsonObject query) {
        JsonObject data = new JsonObject();
        data.addProperty("provider", this.id());
        data.add("echo", query.deepCopy());
        return CompletableFuture.completedFuture(data);
    }
}
