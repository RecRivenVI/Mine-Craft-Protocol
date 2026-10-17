package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import io.github.recrivenvi.minecraftprotocol.safety.WorldClientModel;
import io.github.recrivenvi.minecraftprotocol.safety.WorldDataView;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Authenticated client-model boundary, independent of USER UI and player input contracts. */
final class WorldReadEngine {
    private final ProbeService service;
    private final ProtocolState state;

    WorldReadEngine(ProbeService service, ProtocolState state) {
        this.service = service;
        this.state = state;
    }

    WorldDataView.Category authorize(String value) {
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

    WorldDataView.Category category(JsonObject request) {
        if (request.has("category")
                && (!request.get("category").isJsonPrimitive()
                        || !request.getAsJsonPrimitive("category").isString())) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_WORLD_CATEGORY", 400, "World category must be USER or DEBUG");
        }
        return authorize(request.has("category") ? request.get("category").getAsString() : "USER");
    }

    <T> CompletableFuture<T> guarded(String category, Supplier<CompletableFuture<T>> capture) {
        Runnable publication = publicationGuard(category);
        try {
            return CancellableWork.handle(
                    capture.get(),
                    (value, failure) -> {
                        publication.run();
                        if (failure != null) {
                            Throwable cause = failure;
                            while ((cause instanceof java.util.concurrent.CompletionException
                                            || cause
                                                    instanceof
                                                    java.util.concurrent.ExecutionException)
                                    && cause.getCause() != null) cause = cause.getCause();
                            if (cause instanceof RuntimeException runtime) throw runtime;
                            throw new java.util.concurrent.CompletionException(cause);
                        }
                        return value;
                    });
        } catch (Throwable failure) {
            publication.run();
            return CompletableFuture.failedFuture(failure);
        }
    }

    Runnable publicationGuard(String category) {
        authorize(category);
        long generation = state.controlSession().snapshot().transitionSequence();
        return () -> {
            authorize(category);
            if (generation != state.controlSession().snapshot().transitionSequence()) {
                throw new ProtocolState.ProtocolException(
                        "WORLD_READ_CONTEXT_CHANGED",
                        409,
                        "Control context changed before world read publication");
            }
        };
    }

    CompletableFuture<JsonObject> internal(
            String category, Supplier<CompletableFuture<JsonObject>> capture) {
        if (authorize(category) != WorldDataView.Category.DEBUG) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    409,
                    "This source exposes internal world data; explicit DEBUG is required");
        }
        return guarded(category, () -> CancellableWork.map(capture.get(), WorldDataView::debug));
    }

    CompletableFuture<JsonObject> scene(String category) {
        return scene(category, new JsonObject());
    }

    CompletableFuture<JsonObject> scene(String category, JsonObject query) {
        if (authorize(category) != WorldDataView.Category.USER) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_CATEGORY_UNSUPPORTED", 409, "Bounded client scene supports only USER");
        }
        return user("scene", query, category);
    }

    CompletableFuture<JsonObject> read(
            String categoryValue,
            String source,
            JsonObject query,
            Supplier<CompletableFuture<JsonObject>> internalRead) {
        WorldDataView.Category category = authorize(categoryValue);
        if (category == WorldDataView.Category.USER && !source.startsWith("client/")) {
            throw new ProtocolState.ProtocolException(
                    "WORLD_USER_PERCEPTION_UNAVAILABLE",
                    409,
                    "USER cannot read server or internal sources; no fallback was performed");
        }
        if (category == WorldDataView.Category.USER)
            return user(source.substring(7), query, category.name());
        return guarded(
                category.name(),
                () -> CancellableWork.map(internalRead.get(), WorldDataView::debug));
    }

    private CompletableFuture<JsonObject> user(String kind, JsonObject query, String category) {
        validateUser(kind, query);
        JsonObject detached = query.deepCopy();
        return guarded(category, () -> service.clientWorldRead(kind, detached));
    }

    void validateUser(String kind, JsonObject query) {
        try {
            WorldClientModel.validate(kind, query);
        } catch (WorldClientModel.InvalidQuery invalid) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_WORLD_USER_QUERY", 400, invalid.getMessage());
        }
    }
}
