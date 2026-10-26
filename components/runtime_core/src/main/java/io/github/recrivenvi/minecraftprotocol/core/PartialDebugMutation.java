package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;

/** Known native partial effects must reach evidence consumers even though the request failed. */
final class PartialDebugMutation {
    private PartialDebugMutation() {}

    static JsonObject result(Throwable cause) {
        if (!(cause instanceof ProtocolState.ProtocolException error)
                || !error.code().equals("DEBUG_MUTATION_PARTIAL_NOT_LOADED")) return null;
        return error.details().getAsJsonObject("partialMutation").deepCopy();
    }
}
