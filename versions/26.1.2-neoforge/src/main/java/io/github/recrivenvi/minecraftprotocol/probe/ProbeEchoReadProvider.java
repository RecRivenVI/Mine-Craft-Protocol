package io.github.recrivenvi.minecraftprotocol.probe;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.api.ReadProvider;
import java.util.concurrent.CompletableFuture;

final class ProbeEchoReadProvider implements ReadProvider {
    @Override
    public String id() {
        return "minecraft_protocol_probe:echo";
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

