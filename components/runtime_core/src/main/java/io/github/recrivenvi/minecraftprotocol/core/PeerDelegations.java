package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.CancellableWork;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Server-issued authority bound to a real game connection, independent of the client's token. */
final class PeerDelegations implements AutoCloseable {
    interface Mutation {
        CompletableFuture<JsonObject> apply(
                JsonObject body,
                String armId,
                Runnable guard,
                String requestId,
                Set<String> scopes);
    }

    private final ProtocolState owner;
    private final ProbeService service;
    private final Mutation mutation;
    private final SecurityGate security;
    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private volatile boolean closed;

    PeerDelegations(
            ProtocolState owner, ProbeService service, Mutation mutation, SecurityGate security) {
        this.owner = owner;
        this.service = service;
        this.mutation = mutation;
        this.security = security;
    }

    CompletableFuture<JsonObject> issue(JsonObject request) {
        owner.requireScope("server_peer");
        if (!Set.of("playerUuid", "connectionId", "scopes", "debugArmId", "ttlMs")
                .containsAll(request.keySet()))
            throw failure("INVALID_PEER_DELEGATION", 400, "Unknown delegation field");
        String player = uuid(request, "playerUuid");
        String connection = uuid(request, "connectionId");
        if (!request.has("scopes") || !request.get("scopes").isJsonArray())
            throw failure("INVALID_PEER_DELEGATION", 400, "Explicit read/mutation scopes required");
        Set<String> allowed = new java.util.HashSet<>();
        for (var entry : request.getAsJsonArray("scopes")) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
                throw failure("INVALID_PEER_DELEGATION", 400, "Scopes must be strings");
            String scope = entry.getAsString();
            if (!scope.matches("[a-z][a-z0-9_.:-]{0,127}"))
                throw failure("INVALID_PEER_DELEGATION", 400, "Invalid scope name");
            owner.requireScope(scope);
            allowed.add(scope);
        }
        if (!allowed.containsAll(Set.of("read", "debug", "debug.world.read")))
            throw failure(
                    "INVALID_PEER_DELEGATION",
                    400,
                    "Delegated receipts require independent DEBUG read scopes");
        long ttl;
        try {
            ttl =
                    request.has("ttlMs")
                            ? request.get("ttlMs").getAsBigDecimal().longValueExact()
                            : 15000;
        } catch (RuntimeException invalid) {
            throw failure("INVALID_PEER_DELEGATION", 400, "ttlMs must be an integer");
        }
        if (ttl < 1000 || ttl > 60000)
            throw failure("INVALID_PEER_DELEGATION", 400, "ttlMs must be 1000..60000");
        String arm = request.has("debugArmId") ? required(request, "debugArmId") : null;
        if (allowed.contains("debug.write")) owner.requireDebugCredential(arm);
        long duration = ttl;
        return CancellableWork.compose(
                service.peerAuthorityAvailable(),
                available -> {
                    if (!available)
                        throw failure(
                                "SERVER_PEER_OWNER_UNAVAILABLE",
                                409,
                                "This Runtime owns no live server authority");
                    return CancellableWork.map(
                            service.peerWorldFingerprint(),
                            fingerprint -> {
                                synchronized (this) {
                                    requireOpen();
                                    grants.values()
                                            .removeIf(
                                                    grant ->
                                                            grant.expires
                                                                    <= System.currentTimeMillis());
                                    if (grants.size() >= 32)
                                        throw failure(
                                                "PEER_DELEGATION_LIMIT",
                                                429,
                                                "At most 32 active delegations");
                                    Grant grant =
                                            new Grant(
                                                    player,
                                                    connection,
                                                    Set.copyOf(allowed),
                                                    arm,
                                                    fingerprint
                                                            .get("worldFingerprint")
                                                            .getAsString(),
                                                    fingerprint.get("sessionEpoch").getAsString(),
                                                    System.currentTimeMillis() + duration);
                                    grants.put(grant.id, grant);
                                    owner.audit(
                                            grant.id,
                                            connection,
                                            "/v0/server/peer/delegations",
                                            "peer_authority_issued");
                                    return grant.json();
                                }
                            });
                });
    }

    JsonObject revoke(String id) {
        owner.requireScope("server_peer");
        Grant grant = grants.remove(id);
        if (grant == null) throw failure("PEER_DELEGATION_NOT_FOUND", 404, "Unknown delegation");
        grant.cancel();
        owner.audit(
                id,
                grant.connection,
                "/v0/server/peer/delegations/" + id,
                "peer_authority_revoked");
        JsonObject value = grant.json();
        value.addProperty("revoked", true);
        return value;
    }

    JsonObject list() {
        owner.requireScope("server_peer");
        JsonObject value = new JsonObject();
        JsonArray entries = new JsonArray();
        grants.values().forEach(grant -> entries.add(grant.json()));
        value.addProperty("type", "server.peer.delegations");
        value.add("delegations", entries);
        return value;
    }

    CompletableFuture<JsonObject> execute(
            String player,
            String connection,
            BooleanSupplier operator,
            String operation,
            JsonObject params,
            ServerPeerReceiver.NativeAction nativeRead) {
        Grant grant = grants.get(required(params, "delegationId"));
        if (grant == null)
            throw failure(
                    "PEER_DELEGATION_REQUIRED",
                    403,
                    "Server owner must explicitly delegate this connection");
        Runnable guard = () -> requirePublication(player, connection, operator, params);
        guard.run();
        security.admitPeer(operation);
        boolean legacyRead =
                Set.of("player.get", "world.block.get", "world.entities.query").contains(operation);
        boolean fixture = operation.equals("fixture.player.teleport");
        if (!Set.of("observe.deep", "debug.mutate", "world.fingerprint", "debug.capabilities")
                        .contains(operation)
                && !legacyRead
                && !fixture)
            throw failure(
                    "PEER_OPERATION_UNSUPPORTED",
                    409,
                    "Use formal delegated observation or typed Debug");
        if (!legacyRead
                && !fixture
                && !Set.of("world.fingerprint", "debug.capabilities").contains(operation)
                && (!params.has("request") || !params.get("request").isJsonObject()))
            throw failure("INVALID_PEER_REQUEST", 400, "Typed request required");
        JsonObject body =
                params.has("request")
                        ? params.getAsJsonObject("request").deepCopy()
                        : new JsonObject();
        String requestId =
                params.has("peerRequestId")
                        ? required(params, "peerRequestId")
                        : UUID.randomUUID().toString();
        CompletableFuture<JsonObject> source =
                CancellableWork.compose(
                        service.peerWorldFingerprint(),
                        fp -> {
                            guard.run();
                            if (!grant.fingerprint.equals(fp.get("worldFingerprint").getAsString())
                                    || !grant.epoch.equals(fp.get("sessionEpoch").getAsString()))
                                throw failure(
                                        "PEER_AUTHORITY_REVOKED",
                                        409,
                                        "Delegation belongs to another server world lifetime");
                            if (operation.equals("world.fingerprint"))
                                return CompletableFuture.completedFuture(fp);
                            if (operation.equals("debug.capabilities"))
                                return CancellableWork.map(
                                        service.peerWorldCapabilities(),
                                        capabilities -> {
                                            JsonObject value = capabilities.deepCopy();
                                            value.remove("resourceInspection");
                                            value.addProperty("peerMaxResponseBytes", 30 * 1024);
                                            return value;
                                        });
                            if (legacyRead) return nativeRead.apply(guard, null);
                            if (fixture) {
                                if (!grant.scopes.contains("fixture"))
                                    throw failure(
                                            "SCOPE_DENIED", 403, "No delegated Fixture scope");
                                owner.requireScope("fixture");
                                return owner.operate(work -> nativeRead.apply(guard, work));
                            }
                            if (operation.equals("debug.mutate")) {
                                if (!grant.scopes.contains("debug.write"))
                                    throw failure(
                                            "DEBUG_SCOPE_DENIED",
                                            403,
                                            "No delegated mutation scope");
                                body.remove("actorUuid");
                                String typedOperation = required(body, "operation");
                                if (!body.has("dimension")
                                        || typedOperation.startsWith("player.")
                                        || typedOperation.startsWith("menu."))
                                    body.addProperty("actorUuid", player);
                                return mutation.apply(
                                        body, grant.arm, guard, requestId, grant.scopes);
                            }
                            body.addProperty("category", "DEBUG");
                            body.addProperty("perspective", "server_authoritative");
                            JsonObject budgets =
                                    body.has("budgets")
                                            ? body.getAsJsonObject("budgets")
                                            : new JsonObject();
                            int responseBudget =
                                    budgets.has("maxResponseBytes")
                                            ? budgets.get("maxResponseBytes").getAsInt()
                                            : 30000;
                            budgets.addProperty(
                                    "maxResponseBytes", Math.min(responseBudget, 30000));
                            body.add("budgets", budgets);
                            JsonObject selector =
                                    body.has("selector")
                                            ? body.getAsJsonObject("selector")
                                            : new JsonObject();
                            boolean playerDomain =
                                    body.has("domains")
                                            && body.getAsJsonArray("domains").asList().stream()
                                                    .anyMatch(
                                                            domain ->
                                                                    domain.isJsonPrimitive()
                                                                            && ("player"
                                                                                            .equals(
                                                                                                    domain
                                                                                                            .getAsString())
                                                                                    || "menu"
                                                                                            .equals(
                                                                                                    domain
                                                                                                            .getAsString())));
                            selector.remove("playerUuid");
                            if (playerDomain || !selector.has("dimension"))
                                selector.addProperty("playerUuid", player);
                            body.add("selector", selector);
                            var context =
                                    new DeepObservationRequestContext(
                                            grant.scopes,
                                            owner.principalId(),
                                            requestId,
                                            connection,
                                            Math.min(
                                                    grant.expires,
                                                    System.currentTimeMillis() + 1500),
                                            event -> {},
                                            guard);
                            var read =
                                    CancellableWork.handle(
                                            owner.applyDeadline(
                                                    service.peerWorldObservation(body, context),
                                                    context.deadlineAtMillis()),
                                            (value, error) -> {
                                                guard.run();
                                                if (error != null)
                                                    throw new java.util.concurrent
                                                            .CompletionException(error);
                                                return value;
                                            });
                            read.whenComplete(
                                    (value, error) -> {
                                        if (error != null)
                                            context.cancel("peer_read_cancelled_or_expired");
                                    });
                            return read;
                        });
        grant.pending.add(source);
        source.whenComplete((value, error) -> grant.pending.remove(source));
        if (grants.get(grant.id) != grant) source.cancel(false);
        return CancellableWork.map(
                source,
                value -> {
                    guard.run();
                    owner.audit(
                            requestId, connection, "peer/" + operation, "server_owner_authorized");
                    value.addProperty("serverPrincipalId", owner.principalId());
                    value.addProperty("peerPlayerUuid", player);
                    value.addProperty("peerConnectionId", connection);
                    return value;
                });
    }

    private static final class Grant {
        final String id = UUID.randomUUID().toString(), player, connection, arm, fingerprint, epoch;
        final Set<String> scopes;
        final long expires;
        final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

        Grant(
                String player,
                String connection,
                Set<String> scopes,
                String arm,
                String fingerprint,
                String epoch,
                long expires) {
            this.player = player;
            this.connection = connection;
            this.scopes = scopes;
            this.arm = arm;
            this.fingerprint = fingerprint;
            this.epoch = epoch;
            this.expires = expires;
        }

        void cancel() {
            pending.forEach(f -> f.cancel(false));
            pending.clear();
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("type", "server.peer.delegation");
            value.addProperty("delegationId", id);
            value.addProperty("playerUuid", player);
            value.addProperty("connectionId", connection);
            value.addProperty("expiresAtMillis", expires);
            value.addProperty("worldFingerprint", fingerprint);
            value.addProperty("sessionEpoch", epoch);
            value.addProperty("active", expires > System.currentTimeMillis());
            JsonArray entries = new JsonArray();
            scopes.stream().sorted().forEach(entries::add);
            value.add("scopes", entries);
            return value;
        }
    }

    void requirePublication(
            String player, String connection, BooleanSupplier operator, JsonObject params) {
        requireOpen();
        Grant grant = grants.get(required(params, "delegationId"));
        if (grant == null
                || !grant.player.equals(player)
                || !grant.connection.equals(connection)
                || grant.expires <= System.currentTimeMillis()
                || !operator.getAsBoolean())
            throw failure(
                    "PEER_AUTHORITY_REVOKED",
                    403,
                    "Peer principal, connection, permission or lifetime changed");
        owner.requireScope("server_peer");
        grant.scopes.forEach(owner::requireScope);
    }

    private void requireOpen() {
        if (closed) throw failure("RUNTIME_SHUTTING_DOWN", 409, "Peer receiver is closed");
    }

    static String required(JsonObject value, String key) {
        if (!value.has(key)
                || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isString()
                || value.get(key).getAsString().isBlank()
                || value.get(key).getAsString().length() > 128)
            throw failure("INVALID_PEER_REQUEST", 400, key + " must be a bounded string");
        return value.get(key).getAsString();
    }

    private static String uuid(JsonObject value, String key) {
        String text = required(value, key);
        try {
            String canonical = UUID.fromString(text).toString();
            if (!canonical.equalsIgnoreCase(text)) throw new IllegalArgumentException();
            return canonical;
        } catch (IllegalArgumentException invalid) {
            throw failure("INVALID_PEER_DELEGATION", 400, key + " must be a UUID");
        }
    }

    private static ProtocolState.ProtocolException failure(
            String code, int status, String message) {
        return new ProtocolState.ProtocolException(code, status, message);
    }

    @Override
    public synchronized void close() {
        closed = true;
        invalidate();
    }

    synchronized void invalidate() {
        grants.values().forEach(Grant::cancel);
        grants.clear();
    }
}
