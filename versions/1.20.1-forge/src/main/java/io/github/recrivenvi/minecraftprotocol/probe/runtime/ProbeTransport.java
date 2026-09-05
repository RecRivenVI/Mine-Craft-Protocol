package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class ProbeTransport implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final AttributeKey<String> CONTROL_LEASE_CHANNEL =
            AttributeKey.valueOf("minecraft-protocol-control-lease");
    private static final AttributeKey<String> EVENT_REQUEST_URI =
            AttributeKey.valueOf("minecraft-protocol-event-request-uri");

    private final ProbeService service;
    private final String token;
    private final int port;
    private final SecurityGate securityGate;
    private final ProtocolState protocolState;
    private final AutomationEngine automation;
    private final ObservationEngine observation;
    private final RecordingEngine recording;
    private final EventHub eventHub;
    private final ConditionEngine conditions;
    private final DebugBatchEngine debugBatches;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    ProbeTransport(ProbeService service, String token, int port) {
        this.service = service;
        this.token = token;
        this.port = port;
        this.securityGate = new SecurityGate(
                "token:" + UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)));
        this.automation = new AutomationEngine(service);
        this.protocolState = new ProtocolState(
                ProtocolState.configuredScopes(),
                this.securityGate.principalId(),
                reason -> {
                    this.automation.cancelControlWork();
                    return service.releaseAllInput(reason);
                },
                this::controlPresenceChanged);
        this.service.attachControlSession(this.protocolState.controlSession());
        this.observation = new ObservationEngine(service);
        this.recording = new RecordingEngine(service, this.observation);
        this.eventHub = new EventHub("1.20.1-forge", this::resyncSnapshot);
        this.conditions = new ConditionEngine(
                service, this.protocolState, this.eventHub, this.recording, this.observation);
        this.automation.setConditionEngine(this.conditions);
        this.debugBatches = new DebugBatchEngine(
                service, this.protocolState, this::observeDebugMutation);
    }

    void startAsync() {
        if (!this.started.compareAndSet(false, true)) return;
        Thread starter = new Thread(this::start, "minecraft-protocol-starter");
        starter.setDaemon(true);
        starter.start();
    }

    private void start() {
        this.bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("mcp-boss", true));
        this.workerGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("mcp-worker", true));
        try {
            this.serverChannel = new ServerBootstrap()
                    .group(this.bossGroup, this.workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(MAX_BODY_BYTES));
                            channel.pipeline().addLast(new ChunkedWriteHandler());
                            channel.pipeline().addLast(new AuthorizationHandler());
                            channel.pipeline().addLast(new WebSocketServerProtocolHandler("/v0/events", null, true));
                            channel.pipeline().addLast(new RequestHandler());
                        }
                    })
                    .bind(new InetSocketAddress("127.0.0.1", this.port))
                    .syncUninterruptibly()
                    .channel();
        } catch (Throwable throwable) {
            this.close();
            throw throwable;
        }
    }

    void broadcast(JsonObject event) {
        JsonObject published = this.eventHub.publish(event);
        this.recording.recordEvent("runtime.event", published);
    }

    private void controlPresenceChanged(io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession.Snapshot snapshot) {
        this.service.controlPresenceChanged(snapshot);
        if (this.closed.get()) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "control.presence");
        event.addProperty("category", "control");
        event.addProperty("controlState", snapshot.state().name());
        event.addProperty("reason", snapshot.reason());
        event.addProperty("reconsentRequired", snapshot.reconsentRequired());
        event.addProperty("principalId", this.securityGate.principalId());
        event.addProperty("controlTransitionSequence", snapshot.transitionSequence());
        event.addProperty("mode", snapshot.mode().name());
        event.addProperty("controlSessionId", snapshot.controlSessionId());
        event.addProperty("modeGeneration", snapshot.transitionSequence());
        this.broadcast(event);
    }

    boolean revokeHumanControl() {
        JsonObject result = this.protocolState.revokeHumanControl();
        return result.has("status") && "manually_revoked".equals(result.get("status").getAsString());
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.eventHub.close();
        this.conditions.close();
        this.debugBatches.close();
        this.automation.close();
        this.protocolState.close();
        this.recording.close();
        if (this.serverChannel != null) this.serverChannel.close();
        if (this.workerGroup != null) this.workerGroup.shutdownGracefully();
        if (this.bossGroup != null) this.bossGroup.shutdownGracefully();
    }

    private final class AuthorizationHandler extends ChannelInboundHandlerAdapter {
        private final byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            if (!(message instanceof FullHttpRequest request)) {
                context.fireChannelRead(message);
                return;
            }

            String requestId = UUID.randomUUID().toString();
            try {
                requestId = requestId(request);
                validateHostAndOrigin(request);
                String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
                if (authorization == null || !MessageDigest.isEqual(
                        this.expected, authorization.getBytes(StandardCharsets.UTF_8))) {
                    throw new ProtocolState.ProtocolException("UNAUTHORIZED", 401, "Missing or invalid Bearer token");
                }
                securityGate.admit(context.channel(), request.method().name(),
                        new QueryStringDecoder(request.uri()).path());
                if (new QueryStringDecoder(request.uri()).path().equals("/v0/events")) {
                    protocolState.requireScope("event");
                    context.channel().attr(EVENT_REQUEST_URI).set(request.uri());
                    request.setUri("/v0/events");
                    String leaseId = boundedHeader(request, ProtocolState.LEASE_HEADER);
                    if (leaseId != null) {
                        protocolState.requireLease(leaseId);
                        context.channel().attr(CONTROL_LEASE_CHANNEL).set(leaseId);
                    }
                }
                context.fireChannelRead(message);
            } catch (Throwable throwable) {
                protocolState.audit(requestId, auditConnectionId(context.channel()),
                        new QueryStringDecoder(request.uri()).path(), "rejected");
                sendError(context, requestId, throwable);
                ReferenceCountUtil.release(message);
            }
        }
    }

    private final class RequestHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext context, Object message) {
            if (message instanceof FullHttpRequest request) {
                this.handleHttp(context, request);
            } else if (message instanceof TextWebSocketFrame frame) {
                eventHub.accept(context.channel(), frame.text());
            } else if (message instanceof WebSocketFrame frame && !(frame instanceof TextWebSocketFrame)) {
                frame.retain();
                context.fireChannelRead(frame);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                String requestUri = context.channel().attr(EVENT_REQUEST_URI).get();
                eventHub.register(context.channel(), new QueryStringDecoder(
                        requestUri == null ? "/v0/events" : requestUri));
            }
            super.userEventTriggered(context, event);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            eventHub.unregister(context.channel());
            securityGate.remove(context.channel());
            protocolState.releaseLeaseIfMatches(
                    context.channel().attr(CONTROL_LEASE_CHANNEL).get(), "control_channel_disconnected");
            super.channelInactive(context);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext context) throws Exception {
            eventHub.channelWritable(context.channel());
            super.channelWritabilityChanged(context);
        }

        private void handleHttp(ChannelHandlerContext context, FullHttpRequest request) {
            QueryStringDecoder uri = new QueryStringDecoder(request.uri());
            String path = uri.path();
            ProtocolState.RequestMetadata metadata;
            try {
                metadata = metadata(request);
                this.dispatch(context, request, uri, path, metadata);
            } catch (Throwable throwable) {
                String requestId = safeRequestId(request);
                protocolState.audit(requestId, auditConnectionId(context.channel()), path, "rejected");
                sendError(context, requestId, throwable);
            }
        }

        private void dispatch(
                ChannelHandlerContext context,
                FullHttpRequest request,
                QueryStringDecoder uri,
                String path,
                ProtocolState.RequestMetadata metadata) {
            if (request.method() == HttpMethod.GET && path.equals("/v0/session")) {
                read(context, metadata, path, "read", service::session);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/capabilities")) {
                read(context, metadata, path, "read", service::capabilities);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/readiness")) {
                read(context, metadata, path, "read", service::readiness);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/diagnostics/hooks")) {
                read(context, metadata, path, "diagnostics", service::hookManifest);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/events/stress")) {
                protocolState.requireScope("diagnostics");
                protocolState.requireOperateIntent();
                JsonObject body = jsonBody(request);
                int count = body.has("count") ? body.get("count").getAsInt() : 1;
                int payloadBytes = body.has("payloadBytes") ? body.get("payloadBytes").getAsInt() : 0;
                if (count < 1 || count > 8192 || payloadBytes < 0 || payloadBytes > 4096) {
                    throw new ProtocolState.ProtocolException(
                            "INVALID_EVENT_STRESS", 400, "count must be 1..8192 and payloadBytes 0..4096");
                }
                try (AgentControlSession.OperateWork work = protocolState.beginOperate();
                        AgentControlSession.Guard modeGuard = work.enter()) {
                String filler = "x".repeat(payloadBytes);
                for (int index = 0; index < count; index++) {
                    JsonObject event = new JsonObject();
                    event.addProperty("type", "diagnostics.event.self_test");
                    event.addProperty("category", "diagnostics");
                    event.addProperty("index", index);
                    if (!filler.isEmpty()) event.addProperty("payload", filler);
                    broadcast(event);
                }
                JsonObject result = new JsonObject();
                result.addProperty("type", "diagnostics.event_stress");
                result.addProperty("published", count);
                result.addProperty("payloadBytes", payloadBytes);
                result.addProperty("resumeCursor", eventHub.currentSequence());
                sendImmediate(context, metadata, path, result);
                }
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/server/peer")) {
                read(context, metadata, path, "read", service::peerStatus);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/server/peer/probe")) {
                read(context, metadata, path, "read", service::peerProbe);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/trace")) {
                read(context, metadata, path, "diagnostics", service::trace);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/diagnostics/thread")) {
                read(context, metadata, path, "diagnostics",
                        () -> service.threadProbe(stringQuery(uri, "affinity", "client")));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/ui/test-screen")) {
                protocolState.requireScope("diagnostics");
                protocolState.requireScope("control");
                protocolState.requireScope("fixture");
                CompletableFuture<JsonObject> fixture = protocolState.operate(service::openAutomationProbeScreen);
                fixture.thenAccept(result -> recording.contaminate("FIXTURE", "fixture.ui.test_screen", result));
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(fixture, metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/audit")) {
                protocolState.requireScope("diagnostics");
                sendImmediate(context, metadata, path, protocolState.auditSnapshot(intQuery(uri, "limit", 64)));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/operations")) {
                protocolState.requireScope("read");
                sendImmediate(context, metadata, path, protocolState.descriptors());
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/security/context")) {
                protocolState.requireScope("diagnostics");
                sendImmediate(context, metadata, path, protocolState.securityContext());
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/events/resync")) {
                protocolState.requireScope("event");
                sendJsonFuture(context, metadata, path, resyncSnapshot());
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/ui/tree")) {
                read(context, metadata, path, "ui", service::uiTree);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/ui/resolve")) {
                protocolState.requireScope("ui");
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(automation.resolve(jsonBody(request)), metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/ui/action")) {
                protocolState.requireScope("ui");
                protocolState.requireScope("input");
                protocolState.requireTakeover(metadata.leaseId());
                JsonObject body = jsonBody(request);
                Supplier<CompletableFuture<JsonObject>> action = () ->
                        service.validatePreconditions(metadata.expectedScreenRevision(), metadata.expectedMenuRevision())
                                .thenCompose(ignored -> automation.uiAction(body, () -> protocolState.requireTakeover(metadata.leaseId()),
                                        supplier -> protocolState.admitInput(metadata.leaseId(), supplier)));
                String idempotencyKey = metadata.idempotencyKey() == null
                        ? null : path + ":" + metadata.idempotencyKey();
                CompletableFuture<JsonObject> future = protocolState.idempotent(idempotencyKey, action);
                future.thenAccept(result -> recording.recordEvent("ui.action", result));
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(future, metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/ui/vision/context")) {
                protocolState.requireScope("ui");
                protocolState.requireScope("capture");
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(automation.visionContext(), metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/render/facts")) {
                read(context, metadata, path, "read", service::renderFacts);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/player")) {
                read(context, metadata, path, "read", service::playerState);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/command/player")) {
                protocolState.requireScope("command");
                protocolState.requireScope("control");
                protocolState.requireTakeover(metadata.leaseId());
                JsonObject body = jsonBody(request);
                if (!body.has("command")) {
                    throw new ProtocolState.ProtocolException("INVALID_PLAYER_COMMAND", 400, "Missing command");
                }
                CompletableFuture<JsonObject> command = protocolState.admitInput(metadata.leaseId(),
                        () -> service.playerCommand(body.get("command").getAsString()));
                command.thenAccept(result -> recording.recordEvent("command.player.executed", result));
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(command, metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/server/player")) {
                read(context, metadata, path, "read", service::serverPlayerState);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/world/block")) {
                read(context, metadata, path, "read",
                        () -> service.blockState(intQuery(uri, "x", 0), intQuery(uri, "y", 0), intQuery(uri, "z", 0)));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/server/world/block")) {
                read(context, metadata, path, "read",
                        () -> service.serverBlockState(intQuery(uri, "x", 0), intQuery(uri, "y", 0), intQuery(uri, "z", 0)));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/world/entities")) {
                read(context, metadata, path, "read",
                        () -> service.entities(doubleQuery(uri, "radius", 16.0)));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/server/world/entities")) {
                read(context, metadata, path, "read",
                        () -> service.serverEntities(doubleQuery(uri, "radius", 16.0)));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/providers")) {
                protocolState.requireScope("read");
                sendImmediate(context, metadata, path, observation.descriptors());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/providers/read")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, protocolState.applyDeadline(
                        observation.read(jsonBody(request)), metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/state/frames")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, protocolState.applyDeadline(
                        observation.stateFrame(jsonBody(request)), metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/capture/info")) {
                read(context, metadata, path, "capture", service::captureInfo);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/world/fingerprint")) {
                read(context, metadata, path, "read", service::worldFingerprint);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/debug/status")) {
                protocolState.requireScope("debug");
                sendImmediate(context, metadata, path, protocolState.debugStatus());
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/debug/capabilities")) {
                protocolState.requireScope("debug");
                sendJsonFuture(context, metadata, path,
                        service.phase9cDebugCapabilities().thenApply(capabilities -> {
                            capabilities.add("batch", debugBatches.capabilities());
                            capabilities.add("evidence", protocolState.debugEvidenceStatus());
                            return capabilities;
                        }));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/arm")) {
                protocolState.requireScope("debug");
                JsonObject body = jsonBody(request);
                sendJsonFuture(context, metadata, path,
                        service.worldFingerprint().thenCombine(
                                service.phase9cDebugCapabilities(), (fingerprint, capabilities) ->
                                        protocolState.armDebug(
                                                body.get("worldFingerprint").getAsString(),
                                                fingerprint.get("worldFingerprint").getAsString(),
                                                capabilities.get("sessionEpoch").getAsString(),
                                                stringSet(body, "namespaces"),
                                                optionalLong(body, "ttlMs", 15_000L))));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/renew")) {
                protocolState.requireScope("debug");
                JsonObject body = jsonBody(request);
                sendJsonFuture(context, metadata, path,
                        service.worldFingerprint().thenCombine(
                                service.phase9cDebugCapabilities(), (fingerprint, capabilities) ->
                                        protocolState.renewDebug(
                                                metadata.debugArmId(),
                                                fingerprint.get("worldFingerprint").getAsString(),
                                                capabilities.get("sessionEpoch").getAsString(),
                                                optionalLong(body, "ttlMs", 15_000L))));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/disarm")) {
                protocolState.requireScope("debug");
                sendImmediate(context, metadata, path, protocolState.disarmDebug("client_disarm"));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/mutations")) {
                JsonObject body = jsonBody(request);
                String operation = requiredString(body, "operation");
                String domain = debugDomain(operation);
                protocolState.requireDebugScope(domain);
                protocolState.requireDebugCredential(metadata.debugArmId());
                protocolState.requireOperateIntent();
                body.addProperty("debugOperationId", metadata.requestId());
                JsonObject started = debugEvent("debug.operation.started", body, metadata.requestId());
                broadcast(started);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.phase9cDebugMutation(
                        body, singleDebugAuthorization(metadata, work)));
                mutation.whenComplete((result, error) -> {
                    if (error == null) {
                        observeDebugMutation(result);
                    } else {
                        JsonObject failed = debugEvent(
                                "debug.operation.failed", body, metadata.requestId());
                        Throwable cause = unwrap(error);
                        failed.addProperty("error", cause instanceof ProtocolState.ProtocolException protocolException
                                ? protocolException.code() : cause.getClass().getSimpleName());
                        broadcast(failed);
                    }
                });
                sendJsonFuture(context, metadata, path,
                        protocolState.applyDeadline(mutation, metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/batches")) {
                JsonObject body = jsonBody(request);
                if (!body.has("items") || !body.get("items").isJsonArray()) {
                    throw new ProtocolState.ProtocolException(
                            "INVALID_DEBUG_BATCH", 400, "Debug batch requires items");
                }
                for (com.google.gson.JsonElement item : body.getAsJsonArray("items")) {
                    if (!item.isJsonObject()) throw new ProtocolState.ProtocolException(
                            "INVALID_DEBUG_BATCH", 400, "Every Debug batch item must be an object");
                    protocolState.requireDebugScope(debugDomain(
                            requiredString(item.getAsJsonObject(), "operation")));
                }
                protocolState.requireDebugCredential(metadata.debugArmId());
                protocolState.requireOperateIntent();
                JsonObject operation = protocolState.startOperation(
                        operationId -> protocolState.operate(work -> debugBatches.start(operationId, body, metadata, work)), false);
                sendImmediate(context, metadata, path, operation);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/debug/evidence")) {
                protocolState.requireScope("debug");
                sendImmediate(context, metadata, path, protocolState.debugEvidenceStatus());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/evidence/act/start")) {
                protocolState.requireScope("debug");
                sendImmediate(context, metadata, path, protocolState.startGameplayAct());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/evidence/act/finish")) {
                protocolState.requireScope("debug");
                JsonObject body = jsonBody(request);
                sendImmediate(context, metadata, path,
                        protocolState.finishGameplayAct(requiredString(body, "actId")));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/fixture/player/teleport")) {
                protocolState.requireScope("fixture");
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.fixtureTeleport(
                        body.get("x").getAsDouble(), body.get("y").getAsDouble(), body.get("z").getAsDouble(), work));
                mutation.thenAccept(result -> recording.contaminate("FIXTURE", "fixture.player.teleport", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/player/health")) {
                protocolState.requireScope("debug");
                protocolState.requireDebugCredential(metadata.debugArmId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.worldFingerprint().thenCompose(fingerprint -> {
                    protocolState.requireDebugArm(
                            metadata.debugArmId(), fingerprint.get("worldFingerprint").getAsString());
                    return service.debugSetHealth(body.get("health").getAsFloat(), work);
                }));
                mutation.thenAccept(result ->
                        recording.contaminate("DEBUG_PRIVILEGED", "debug.player.health", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/player/attribute")) {
                protocolState.requireScope("debug");
                protocolState.requireDebugCredential(metadata.debugArmId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.worldFingerprint().thenCompose(fingerprint -> {
                    protocolState.requireDebugArm(
                            metadata.debugArmId(), fingerprint.get("worldFingerprint").getAsString());
                    return service.phase9aDebugAttribute(
                            body.get("attributeId").getAsString(), body.get("value").getAsDouble(), work);
                }));
                mutation.thenAccept(result -> recording.contaminate(
                        "DEBUG_PRIVILEGED", "debug.player.attribute.set", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/entity/state")) {
                protocolState.requireScope("debug");
                protocolState.requireDebugCredential(metadata.debugArmId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.worldFingerprint().thenCompose(fingerprint -> {
                    protocolState.requireDebugArm(
                            metadata.debugArmId(), fingerprint.get("worldFingerprint").getAsString());
                    return service.phase9aDebugEntityState(
                            body.get("entityUuid").getAsString(),
                            body.get("state").getAsString(), body.get("value").getAsBoolean(), work);
                }));
                mutation.thenAccept(result -> recording.contaminate(
                        "DEBUG_PRIVILEGED", "debug.entity.state.set", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/phase9a/scenario")) {
                protocolState.requireScope("debug");
                protocolState.requireDebugCredential(metadata.debugArmId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.worldFingerprint().thenCompose(fingerprint -> {
                    protocolState.requireDebugArm(
                            metadata.debugArmId(), fingerprint.get("worldFingerprint").getAsString());
                    return service.phase9aDebugScenario(body, work);
                }));
                mutation.thenAccept(result -> recording.contaminate(
                        "DEBUG_PRIVILEGED", "debug.phase9a.scenario", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/debug/world/block")) {
                protocolState.requireScope("debug");
                protocolState.requireDebugCredential(metadata.debugArmId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> mutation = protocolState.operate(work -> service.worldFingerprint().thenCompose(fingerprint -> {
                    protocolState.requireDebugArm(
                            metadata.debugArmId(), fingerprint.get("worldFingerprint").getAsString());
                    return service.debugSetBlock(
                            body.get("x").getAsInt(), body.get("y").getAsInt(), body.get("z").getAsInt(),
                            body.get("blockId").getAsString(), nullableString(body, "expectedBlockId"), work);
                }));
                mutation.thenAccept(result ->
                        recording.contaminate("DEBUG_PRIVILEGED", "debug.world.block", result));
                sendJsonFuture(context, metadata, path, mutation);
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/diagnostics/phase9a/inventory")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, service.phase9aInventory());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/phase9a/observe")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, service.phase9aObserve(jsonBody(request)));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/phase9a/storage/read")) {
                protocolState.requireScope("storage.read");
                sendJsonFuture(context, metadata, path, service.phase9aStorageRead(jsonBody(request)));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/phase9a/keyframe")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, service.phase9aKeyframe(jsonBody(request)));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/phase9a/delta")) {
                protocolState.requireScope("read");
                JsonObject body = jsonBody(request);
                sendJsonFuture(context, metadata, path,
                        service.phase9aDelta(body.get("baseSnapshotId").getAsString()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/diagnostics/phase9a/reconstruct")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, service.phase9aReconstruct(jsonBody(request)));

            } else if (request.method() == HttpMethod.GET && path.equals("/v0/observe/deep/capabilities")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, service.formalObservationCapabilities());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/observe/deep")) {
                protocolState.requireScope("read");
                DeepObservationRequestContext observationContext = protocolState.deepObservationContext(
                        metadata, auditConnectionId(context.channel()));
                protocolState.registerDeepObservation(metadata.requestId(), observationContext);
                CompletableFuture<JsonObject> source =
                        service.formalDeepObservation(jsonBody(request), observationContext);
                CompletableFuture<JsonObject> guarded =
                        protocolState.applyDeadline(source, metadata.deadlineAtMillis());
                context.channel().closeFuture().addListener(ignored ->
                        observationContext.cancel("client_disconnect"));
                guarded.whenComplete((value, error) -> {
                    if (error != null) observationContext.cancel(
                            error instanceof TimeoutException ? "request_deadline" : "request_cancelled");
                    protocolState.unregisterDeepObservation(metadata.requestId(), observationContext);
                });
                sendJsonFuture(context, metadata, path, guarded);
            } else if (request.method() == HttpMethod.DELETE && path.startsWith("/v0/requests/")) {
                protocolState.requireScope("read");
                String cancelledRequestId = path.substring("/v0/requests/".length());
                if (cancelledRequestId.isBlank()) {
                    throw new ProtocolState.ProtocolException(
                            "INVALID_REQUEST_ID", 400, "Missing request ID to cancel");
                }
                sendImmediate(context, metadata, path,
                        protocolState.cancelDeepObservation(cancelledRequestId));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/recordings")) {
                protocolState.requireScope("read");
                sendImmediate(context, metadata, path, recording.list());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/recordings")) {
                protocolState.requireScope("read");
                protocolState.requireScope("capture");
                sendImmediate(context, metadata, path, recording.start(jsonBody(request)));
            } else if (path.startsWith("/v0/recordings/")) {
                protocolState.requireScope("read");
                String tail = path.substring("/v0/recordings/".length());
                if (request.method() == HttpMethod.GET && tail.endsWith("/artifact")) {
                    String recordingId = tail.substring(0, tail.length() - "/artifact".length());
                    sendArtifactFuture(context, metadata, path, recording.artifact(recordingId));
                } else if (request.method() == HttpMethod.GET) {
                    sendImmediate(context, metadata, path, recording.status(tail));
                } else if (request.method() == HttpMethod.DELETE) {
                    sendImmediate(context, metadata, path, recording.stop(tail, "client_stop"));
                } else {
                    throw new ProtocolState.ProtocolException(
                            "METHOD_NOT_ALLOWED", 405, "Unsupported recording method");
                }
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/capture")) {
                protocolState.requireScope("capture");
                sendBytesFuture(context, metadata, path,
                        protocolState.applyDeadline(service.capturePng(), metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/wait/screen")) {
                read(context, metadata, path, "read",
                        () -> service.waitForScreen(
                                stringQuery(uri, "classContains", ""),
                                longQuery(uri, "timeoutMs", 5_000L)));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/wait/until")) {
                protocolState.requireScope("read");
                JsonObject body = jsonBody(request);
                sendJsonFuture(context, metadata, path, protocolState.applyDeadline(
                        conditions.waitUntil(body.getAsJsonObject("condition"), optionalLong(body, "timeoutMs", 5_000L)),
                        metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/assert")) {
                protocolState.requireScope("read");
                sendJsonFuture(context, metadata, path, protocolState.applyDeadline(
                        conditions.assertThat(jsonBody(request).getAsJsonObject("condition")),
                        metadata.deadlineAtMillis()));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/pipelines")) {
                protocolState.requireScope("input");
                protocolState.requireTakeover(metadata.leaseId());
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> pipeline = automation.executePipeline(
                        body, () -> protocolState.requireTakeover(metadata.leaseId()),
                        supplier -> protocolState.admitInput(metadata.leaseId(), supplier));
                JsonObject operation = protocolState.startOperation(pipeline, true);
                JsonObject startedEvent = operation.deepCopy();
                startedEvent.addProperty("type", "event.pipeline.started");
                broadcast(startedEvent);
                pipeline.whenComplete((result, failure) -> {
                    JsonObject event = protocolState.operationStatus(operation.get("operationId").getAsString());
                    event.addProperty("type", "event.pipeline.terminal");
                    broadcast(event);
                });
                sendImmediate(context, metadata, path, operation);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/operations/wait/screen")) {
                protocolState.requireScope("read");
                JsonObject body = jsonBody(request);
                CompletableFuture<JsonObject> future = protocolState.applyDeadline(
                        service.waitForScreen(
                                body.get("classContains").getAsString(),
                                optionalLong(body, "timeoutMs", 5_000L)),
                        metadata.deadlineAtMillis());
                sendImmediate(context, metadata, path, protocolState.startOperation(future));
            } else if (path.startsWith("/v0/operations/")) {
                String operationPath = path.substring("/v0/operations/".length());
                boolean waitRequest = operationPath.endsWith("/wait");
                String operationId = waitRequest
                        ? operationPath.substring(0, operationPath.length() - "/wait".length())
                        : operationPath;
                protocolState.requireScope("read");
                if (request.method() == HttpMethod.GET) {
                    sendImmediate(context, metadata, path, protocolState.operationStatus(operationId));
                } else if (request.method() == HttpMethod.DELETE) {
                    sendImmediate(context, metadata, path, protocolState.cancelOperation(operationId));
                } else if (request.method() == HttpMethod.POST && waitRequest) {
                    JsonObject body = jsonBody(request);
                    sendJsonFuture(context, metadata, path, protocolState.waitOperation(
                            operationId, optionalLong(body, "timeoutMs", 60_000L)));
                } else {
                    throw new ProtocolState.ProtocolException("METHOD_NOT_ALLOWED", 405, "Unsupported operation method");
                }
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/control/mode")) {
                protocolState.requireScope("read");
                sendImmediate(context, metadata, path, protocolState.modeStatus());
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/control/mode")) {
                protocolState.requireScope("control");
                JsonObject body = jsonBody(request);
                for (String key : body.keySet()) if (!java.util.Set.of("mode", "expectedModeVersion").contains(key))
                    throw new ProtocolState.ProtocolException("INVALID_MODE_REQUEST", 400, "Unknown mode request field");
                AgentControlSession.Mode mode;
                try { mode = AgentControlSession.Mode.valueOf(requiredString(body, "mode")); }
                catch (IllegalArgumentException invalid) {
                    throw new ProtocolState.ProtocolException("INVALID_MODE", 400, "Mode must be READ, OPERATE or TAKEOVER");
                }
                String key = metadata.idempotencyKey() == null ? null : path + ":" + metadata.idempotencyKey() + ":" + body;
                sendJsonFuture(context, metadata, path, protocolState.idempotent(key,
                        () -> protocolState.selectMode(mode, body.getAsJsonObject("expectedModeVersion"),
                                metadata.leaseId(), metadata.requestId(), auditConnectionId(context.channel()))));
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/control/status")) {
                protocolState.requireScope("control");
                sendImmediate(context, metadata, path, protocolState.leaseStatus());
            } else if (request.method() == HttpMethod.GET && path.equals("/v0/input/state")) {
                read(context, metadata, path, "diagnostics", service::inputState);
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/control/acquire")) {
                protocolState.requireScope("control");
                JsonObject body = jsonBody(request);
                for (String key : body.keySet()) if (!java.util.Set.of("ttlMs", "expectedModeVersion").contains(key))
                    throw new ProtocolState.ProtocolException("INVALID_CONTROL_REQUEST", 400, "Unknown acquire request field");
                sendImmediate(context, metadata, path,
                        protocolState.acquireLease(optionalLong(body, "ttlMs", 15_000L),
                                body.has("expectedModeVersion") ? body.getAsJsonObject("expectedModeVersion") : null));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/control/renew")) {
                protocolState.requireScope("control");
                JsonObject body = jsonBody(request);
                sendImmediate(context, metadata, path,
                        protocolState.renewLease(metadata.leaseId(), optionalLong(body, "ttlMs", 15_000L)));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/control/release")) {
                protocolState.requireScope("control");
                sendJsonFuture(context, metadata, path,
                        protocolState.releaseLeaseAndWait(metadata.leaseId(), "client_release"));
            } else if (request.method() == HttpMethod.POST && path.equals("/v0/control/emergency-release")) {
                protocolState.requireScope("control");
                sendJsonFuture(context, metadata, path,
                        protocolState.emergencyReleaseAndWait("emergency_release"));
            } else if (request.method() == HttpMethod.POST && path.startsWith("/v0/input/")) {
                this.handleInput(context, path, metadata, jsonBody(request));
            } else {
                throw new ProtocolState.ProtocolException("NOT_FOUND", 404, "Unknown endpoint: " + path);
            }
        }

        private void handleInput(
                ChannelHandlerContext context,
                String path,
                ProtocolState.RequestMetadata metadata,
                JsonObject body) {
            protocolState.requireScope("input");
            protocolState.requireTakeover(metadata.leaseId());

            Supplier<CompletableFuture<JsonObject>> action = () ->
                    service.validatePreconditions(metadata.expectedScreenRevision(), metadata.expectedMenuRevision())
                            .thenCompose(ignored -> protocolState.admitInput(metadata.leaseId(), () -> {
                                if (path.equals("/v0/input/mouse/move")) {
                                    return service.mouseMove(body.get("x").getAsDouble(), body.get("y").getAsDouble());
                                } else if (path.equals("/v0/input/mouse/button")) {
                                    return service.mouseButton(
                                            body.get("button").getAsInt(),
                                            body.get("action").getAsInt(),
                                            optionalInt(body, "modifiers"));
                                } else if (path.equals("/v0/input/mouse/scroll")) {
                                    return service.mouseScroll(
                                            optionalDouble(body, "xOffset"),
                                            optionalDouble(body, "yOffset"));
                                } else if (path.equals("/v0/input/key")) {
                                    return service.key(
                                            body.get("key").getAsInt(),
                                            optionalInt(body, "scanCode"),
                                            body.get("action").getAsInt(),
                                            optionalInt(body, "modifiers"));
                                }
                                return CompletableFuture.failedFuture(
                                        new ProtocolState.ProtocolException("NOT_FOUND", 404, "Unknown input endpoint"));
                            }));

            String idempotencyKey = metadata.idempotencyKey() == null
                    ? null : path + ":" + metadata.idempotencyKey();
            CompletableFuture<JsonObject> future = protocolState.idempotent(idempotencyKey, action);
            future.thenAccept(result -> {
                JsonObject event = result.deepCopy();
                event.addProperty("type", "event.input.dispatched");
                broadcast(event);
            });
            sendJsonFuture(context, metadata, path, protocolState.applyDeadline(future, metadata.deadlineAtMillis()));
        }

        private void read(
                ChannelHandlerContext context,
                ProtocolState.RequestMetadata metadata,
                String path,
                String scope,
                Supplier<CompletableFuture<JsonObject>> action) {
            protocolState.requireScope(scope);
            sendJsonFuture(context, metadata, path,
                    protocolState.applyDeadline(action.get(), metadata.deadlineAtMillis()));
        }
    }

    private CompletableFuture<JsonObject> resyncSnapshot() {
        CompletableFuture<JsonObject> session = this.service.session();
        CompletableFuture<JsonObject> capabilities = this.service.capabilities();
        CompletableFuture<JsonObject> ui = this.service.uiTree();
        CompletableFuture<JsonObject> player = this.service.playerState();
        return CompletableFuture.allOf(session, capabilities, ui, player).thenApply(ignored -> {
            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("type", "event.resync");
            snapshot.addProperty("resumeCursor", this.eventHub.currentSequence());
            snapshot.addProperty("subscriptionCount", this.eventHub.subscriptionCount());
            snapshot.add("session", session.join());
            snapshot.add("capabilities", capabilities.join());
            snapshot.add("ui", ui.join());
            snapshot.add("player", player.join());
            snapshot.add("operations", this.protocolState.operationSnapshot());
            return snapshot;
        });
    }

    private ProtocolState.RequestMetadata metadata(FullHttpRequest request) {
        String requestId = requestId(request);
        String protocolVersion = boundedHeader(request, ProtocolState.PROTOCOL_HEADER);
        if (protocolVersion == null) protocolVersion = ProtocolState.PROTOCOL_VERSION;
        if (!ProtocolState.PROTOCOL_VERSION.equals(protocolVersion)) {
            throw new ProtocolState.ProtocolException(
                    "PROTOCOL_VERSION_UNSUPPORTED", 426, "Supported protocol version is v0");
        }
        long deadlineAt = 0L;
        String deadline = request.headers().get(ProtocolState.DEADLINE_HEADER);
        if (deadline != null) {
            long relative = Long.parseLong(deadline);
            deadlineAt = System.currentTimeMillis() + relative;
        }
        String idempotency = boundedHeader(request, ProtocolState.IDEMPOTENCY_HEADER);
        return new ProtocolState.RequestMetadata(
                requestId,
                protocolVersion,
                deadlineAt,
                boundedHeader(request, ProtocolState.LEASE_HEADER),
                idempotency,
                longHeader(request, ProtocolState.EXPECTED_SCREEN_HEADER),
                longHeader(request, ProtocolState.EXPECTED_MENU_HEADER),
                boundedHeader(request, ProtocolState.DEBUG_ARM_HEADER));
    }

    private void sendImmediate(
            ChannelHandlerContext context,
            ProtocolState.RequestMetadata metadata,
            String path,
            JsonObject json) {
        json.addProperty("requestId", metadata.requestId());
        json.addProperty("protocolVersion", metadata.protocolVersion());
        protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "completed");
        sendBytes(context, metadata.requestId(), GSON.toJson(json).getBytes(CharsetUtil.UTF_8),
                "application/json; charset=utf-8", HttpResponseStatus.OK);
    }

    private void sendJsonFuture(
            ChannelHandlerContext context,
            ProtocolState.RequestMetadata metadata,
            String path,
            CompletableFuture<JsonObject> future) {
        future.whenComplete((json, error) -> {
            if (error != null) {
                protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "failed");
                sendError(context, metadata.requestId(), error);
            } else {
                sendImmediate(context, metadata, path, json);
            }
        });
    }

    private void sendBytesFuture(
            ChannelHandlerContext context,
            ProtocolState.RequestMetadata metadata,
            String path,
            CompletableFuture<byte[]> future) {
        future.whenComplete((bytes, error) -> {
            if (error != null) {
                protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "failed");
                sendError(context, metadata.requestId(), error);
            } else {
                protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "completed");
                sendBytes(context, metadata.requestId(), bytes, "image/png", HttpResponseStatus.OK);
            }
        });
    }

    private void sendArtifactFuture(
            ChannelHandlerContext context,
            ProtocolState.RequestMetadata metadata,
            String path,
            CompletableFuture<Path> future) {
        future.whenComplete((artifact, error) -> {
            if (error != null) {
                protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "failed");
                sendError(context, metadata.requestId(), error);
            } else {
                try {
                    long length = Files.size(artifact);
                    FileChannel file = FileChannel.open(artifact, StandardOpenOption.READ);
                    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/zip");
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    response.headers().set(ProtocolState.REQUEST_ID_HEADER, metadata.requestId());
                    response.headers().set(ProtocolState.PROTOCOL_HEADER, ProtocolState.PROTOCOL_VERSION);
                    HttpUtil.setContentLength(response, length);
                    protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "completed");
                    context.write(response);
                    context.writeAndFlush(new HttpChunkedInput(new ChunkedNioFile(file, 0L, length, 8192)));
                } catch (Throwable throwable) {
                    protocolState.audit(metadata.requestId(), auditConnectionId(context.channel()), path, "failed");
                    sendError(context, metadata.requestId(), throwable);
                }
            }
        });
    }

    private static JsonObject jsonBody(FullHttpRequest request) {
        if (!request.content().isReadable()) return new JsonObject();
        return JsonParser.parseString(request.content().toString(CharsetUtil.UTF_8)).getAsJsonObject();
    }

    private static String auditConnectionId(Channel channel) {
        String connectionId = channel.attr(SecurityGate.CONNECTION_ID).get();
        return connectionId == null ? "unauthenticated" : connectionId;
    }

    private static void validateHostAndOrigin(FullHttpRequest request) {
        String host = request.headers().get(HttpHeaderNames.HOST);
        String hostName = host == null ? "" : host.trim();
        int separator = hostName.lastIndexOf(':');
        if (separator > 0 && hostName.indexOf(':') == separator) hostName = hostName.substring(0, separator);
        if (!(hostName.equals("127.0.0.1") || hostName.equalsIgnoreCase("localhost"))) {
            throw new ProtocolState.ProtocolException("HOST_REJECTED", 403, "Host must be loopback");
        }
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            try {
                String originHost = URI.create(origin).getHost();
                if (!"127.0.0.1".equals(originHost) && !"localhost".equalsIgnoreCase(originHost)) {
                    throw new ProtocolState.ProtocolException("ORIGIN_REJECTED", 403, "Origin must be loopback");
                }
            } catch (IllegalArgumentException exception) {
                throw new ProtocolState.ProtocolException("ORIGIN_REJECTED", 403, "Origin is invalid");
            }
        }
    }

    private static String requestId(FullHttpRequest request) {
        String value = request.headers().get(ProtocolState.REQUEST_ID_HEADER);
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        if (value.length() > 128) {
            throw new ProtocolState.ProtocolException("INVALID_REQUEST_ID", 400, "Request ID is too long");
        }
        return value;
    }

    private static String safeRequestId(FullHttpRequest request) {
        try {
            return requestId(request);
        } catch (RuntimeException ignored) {
            return UUID.randomUUID().toString();
        }
    }

    private static String boundedHeader(FullHttpRequest request, String name) {
        String value = request.headers().get(name);
        if (value == null || value.isBlank()) return null;
        if (value.length() > 128) {
            throw new ProtocolState.ProtocolException("INVALID_HEADER", 400, name + " is too long");
        }
        return value;
    }

    private static Long longHeader(FullHttpRequest request, String name) {
        String value = request.headers().get(name);
        return value == null || value.isBlank() ? null : Long.parseLong(value);
    }

    private static int optionalInt(JsonObject body, String name) {
        return body.has(name) ? body.get(name).getAsInt() : 0;
    }

    private static long optionalLong(JsonObject body, String name, long fallback) {
        return body.has(name) ? body.get(name).getAsLong() : fallback;
    }

    private static double optionalDouble(JsonObject body, String name) {
        return body.has(name) ? body.get(name).getAsDouble() : 0.0;
    }

    private static String stringQuery(QueryStringDecoder decoder, String name, String fallback) {
        List<String> values = decoder.parameters().get(name);
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }

    private static String nullableString(JsonObject body, String name) {
        return body.has(name) && !body.get(name).isJsonNull() ? body.get(name).getAsString() : null;
    }

    private DebugMutationAuthorization singleDebugAuthorization(
            ProtocolState.RequestMetadata metadata, AgentControlSession.OperateWork work) {
        return new DebugMutationAuthorization() {
            @Override
            public Permit authorize(
                    String currentWorldFingerprint,
                    String sessionEpoch,
                    String domain,
                    String namespace) {
                protocolState.requireDebugAuthorization(
                        metadata.debugArmId(), currentWorldFingerprint,
                        sessionEpoch, domain, namespace);
                AgentControlSession.Guard modeGuard = work.enter();
                return modeGuard::close;
            }

            @Override
            public boolean hasScope(String scope) {
                return protocolState.hasScope(scope);
            }

            @Override
            public String principalId() {
                return protocolState.principalId();
            }

            @Override
            public String debugArmId() {
                return metadata.debugArmId();
            }

            @Override
            public boolean isCancelled() {
                return work.isCancelled();
            }
        };
    }

    private void observeDebugMutation(JsonObject result) {
        String operationId = result.has("debugOperationId")
                ? result.get("debugOperationId").getAsString() : UUID.randomUUID().toString();
        String namespace = result.has("namespace")
                ? result.get("namespace").getAsString() : "unknown";
        String operation = result.has("operation")
                ? result.get("operation").getAsString() : "debug.mutation";
        recording.contaminate("DEBUG_PRIVILEGED", operation, result);
        JsonObject evidence = protocolState.noteDebugMutation(operationId, namespace, operation);
        JsonObject completed = result.deepCopy();
        completed.addProperty("type", "debug.operation.completed");
        completed.add("evidenceWindow", evidence);
        broadcast(completed);
        JsonObject changed = new JsonObject();
        changed.addProperty("type", "resource.changed");
        changed.addProperty("category", "debug");
        changed.addProperty("causedByDebugOperationId", operationId);
        changed.addProperty("operation", operation);
        if (result.has("afterResourceVersion")) {
            changed.add("resourceVersion", result.get("afterResourceVersion").deepCopy());
        }
        broadcast(changed);
    }

    private static JsonObject debugEvent(
            String type, JsonObject request, String operationId) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.addProperty("category", "debug");
        event.addProperty("debugOperationId", operationId);
        if (request.has("operation")) {
            event.addProperty("operation", request.get("operation").getAsString());
        }
        event.addProperty("evidence", "diagnostic");
        return event;
    }

    private static String debugDomain(String operation) {
        int separator = operation.indexOf('.');
        String domain = separator < 0 ? "" : operation.substring(0, separator);
        if (!List.of(
                "player", "entity", "world", "block_entity", "chunk",
                "menu", "client", "network", "provider").contains(domain)) {
            throw new ProtocolState.ProtocolException(
                    "CAPABILITY_UNAVAILABLE", 409, "Unknown typed Debug domain: " + domain);
        }
        return domain;
    }

    private static String requiredString(JsonObject body, String name) {
        if (!body.has(name) || !body.get(name).isJsonPrimitive()
                || body.get(name).getAsString().isBlank()) {
            throw new ProtocolState.ProtocolException(
                    "VALUE_PRECONDITION_FAILED", 400, "Missing " + name);
        }
        return body.get(name).getAsString();
    }

    private static java.util.Set<String> stringSet(JsonObject body, String name) {
        if (!body.has(name)) return java.util.Set.of();
        if (!body.get(name).isJsonArray()) {
            throw new ProtocolState.ProtocolException(
                    "INVALID_DEBUG_ARM", 400, name + " must be an array");
        }
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        for (com.google.gson.JsonElement element : body.getAsJsonArray(name)) {
            values.add(element.getAsString());
        }
        return values;
    }

    private static int intQuery(QueryStringDecoder decoder, String name, int fallback) {
        return Integer.parseInt(stringQuery(decoder, name, Integer.toString(fallback)));
    }

    private static long longQuery(QueryStringDecoder decoder, String name, long fallback) {
        return Long.parseLong(stringQuery(decoder, name, Long.toString(fallback)));
    }

    private static double doubleQuery(QueryStringDecoder decoder, String name, double fallback) {
        return Double.parseDouble(stringQuery(decoder, name, Double.toString(fallback)));
    }

    private void sendError(ChannelHandlerContext context, String requestId, Throwable throwable) {
        Throwable error = unwrap(throwable);
        int status = 500;
        String code = error.getClass().getSimpleName();
        if (error instanceof ProtocolState.ProtocolException protocolException) {
            status = protocolException.httpStatus();
            code = protocolException.code();
        } else if (error instanceof AgentControlSession.ModeException modeError) {
            status = 409;
            code = modeError.code();
        } else if (error instanceof TimeoutException) {
            status = 408;
            code = "REQUEST_DEADLINE_EXCEEDED";
        }
        JsonObject json = new JsonObject();
        json.addProperty("error", code);
        json.addProperty("message", error.getMessage() == null ? "unknown" : error.getMessage());
        if ("USER_MANUALLY_ENDED_CONTROL".equals(code)) {
            json.addProperty("controlState", "MANUALLY_REVOKED");
            json.addProperty("reconsentRequired", true);
            json.addProperty("manualRevocationReason", "human_manual_revocation");
        }
        if (code.startsWith("MODE_") || code.startsWith("STALE_MODE") || code.startsWith("STALE_CONTROL")
                || code.startsWith("TAKEOVER_") || code.equals("OPERATE_REQUIRED")
                || code.equals("USER_MANUALLY_ENDED_CONTROL") || code.startsWith("CONTROL_")) {
            json.add("control", this.protocolState.modeStatus());
        }
        json.addProperty("requestId", requestId);
        json.addProperty("protocolVersion", ProtocolState.PROTOCOL_VERSION);
        sendBytes(context, requestId, GSON.toJson(json).getBytes(CharsetUtil.UTF_8),
                "application/json; charset=utf-8", HttpResponseStatus.valueOf(status));
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current.getClass().getName().equals("java.util.concurrent.ExecutionException"))
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static Throwable unwrapForInternalUse(Throwable throwable) {
        return unwrap(throwable);
    }

    private static void sendBytes(
            ChannelHandlerContext context,
            String requestId,
            byte[] bytes,
            String contentType,
            HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().set(ProtocolState.REQUEST_ID_HEADER, requestId);
        response.headers().set(ProtocolState.PROTOCOL_HEADER, ProtocolState.PROTOCOL_VERSION);
        HttpUtil.setContentLength(response, bytes.length);
        context.writeAndFlush(response);
    }
}
