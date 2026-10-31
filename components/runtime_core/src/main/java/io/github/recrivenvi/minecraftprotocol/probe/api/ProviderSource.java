package io.github.recrivenvi.minecraftprotocol.probe.api;

import com.google.gson.JsonObject;
import java.util.List;

/** Additional per-Runtime contributions, without mutating the process-wide native SPI. */
public interface ProviderSource {
    List<AgentDataProviderV2> snapshot();

    boolean owns(String providerId);

    ProviderSchemaRegistry.ValidationResult validate(
            String providerId, String schemaId, JsonObject value);
}
