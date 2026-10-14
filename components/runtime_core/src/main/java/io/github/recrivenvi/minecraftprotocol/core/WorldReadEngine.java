package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import io.github.recrivenvi.minecraftprotocol.safety.WorldDataView;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Authenticated basic-read boundary, separate from the pending full world perception model. */
final class WorldReadEngine {
    private final ProbeService service;
    private final ProtocolState state;

    WorldReadEngine(ProbeService service, ProtocolState state) {
        this.service = service;
        this.state = state;
    }

    private WorldDataView.Category authorize(String value) {
        state.requireScope("read");
        WorldDataView.Category category;
        try {
            category = WorldDataView.Category.parse(value);
        } catch (IllegalArgumentException error) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_WORLD_CATEGORY", 400, "World category must be USER or DEBUG");
        }
        WorldDataView.requireReadScopes(category, state::requireScope);
        var snapshot = state.controlSession().snapshot();
        try {
            state.controlSession()
                    .requireVersion(snapshot.controlSessionId(), snapshot.transitionSequence());
        } catch (AgentControlSession.ModeException error) {
            throw new ProtocolState.ProtocolException(error.code(), 409, error.getMessage());
        }
        if (!WorldDataView.allowsRead(state.controlSession().snapshot().mode().name(), category)) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_CATEGORY_MODE_MISMATCH",
                    409,
                    "READ supports USER/DEBUG; TAKEOVER only USER; OPERATE only DEBUG");
        }
        return category;
    }

    CompletableFuture<JsonObject> read(
            String categoryValue,
            boolean userPlayer,
            Supplier<CompletableFuture<JsonObject>> internalRead) {
        WorldDataView.Category category = authorize(categoryValue);
        long generation = state.controlSession().snapshot().transitionSequence();
        if (category == WorldDataView.Category.USER && !userPlayer) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    409,
                    "USER scene/server perception is not implemented; no internal read or LIVE/PERSISTED fallback was performed");
        }
        CompletableFuture<JsonObject> future =
                category == WorldDataView.Category.USER ? service.uiTree() : internalRead.get();
        return CancellableWork.compose(
                future,
                captured -> {
                    authorize(category.name());
                    if (generation != state.controlSession().snapshot().transitionSequence()) {
                        throw new ProtocolState.ProtocolException(
                                "WORLD_READ_CONTEXT_CHANGED",
                                409,
                                "Control context changed before world read publication");
                    }
                    return CompletableFuture.completedFuture(
                            category == WorldDataView.Category.USER
                                    ? WorldDataView.userPlayer(captured)
                                    : WorldDataView.debug(captured));
                });
    }
}
