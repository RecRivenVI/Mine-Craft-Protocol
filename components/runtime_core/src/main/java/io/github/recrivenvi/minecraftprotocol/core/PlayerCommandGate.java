package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.safety.AgentControlSession;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native callback rejection is reported only after returning from Vanilla's crash-wrapped UI. */
public final class PlayerCommandGate implements AutoCloseable {
    public enum ConfigurationKind {
        COMMAND_BLOCK("COMMAND", null),
        COMMAND_MINECART("COMMAND", null),
        JIGSAW_BLOCK("BLOCK_STATE", null),
        JIGSAW_GENERATE(null, PlayerCommandPolicy.Effect.UNRESOLVED),
        STRUCTURE_UPDATE("STRUCTURE_PARAMETERS", PlayerCommandPolicy.Effect.STATE_MUTATION),
        STRUCTURE_SCAN("STRUCTURE_PARAMETERS", PlayerCommandPolicy.Effect.STATE_MUTATION),
        STRUCTURE_SAVE(null, PlayerCommandPolicy.Effect.CONTROL_PLANE),
        STRUCTURE_LOAD(null, PlayerCommandPolicy.Effect.UNRESOLVED),
        TEST_BLOCK(null, PlayerCommandPolicy.Effect.CONTROL_PLANE),
        TEST_INSTANCE(null, PlayerCommandPolicy.Effect.CONTROL_PLANE),
        CUSTOM_UI_CALLBACK(null, PlayerCommandPolicy.Effect.UNRESOLVED);

        final String payloadType;
        final PlayerCommandPolicy.Effect fixedEffect;

        ConfigurationKind(String payloadType, PlayerCommandPolicy.Effect fixedEffect) {
            this.payloadType = payloadType;
            this.fixedEffect = fixedEffect;
        }
    }

    private final AgentControlSession control;
    private final BiConsumer<String, Boolean> authorize;
    private final Consumer<JsonObject> observeSubmission;
    private final ThreadLocal<Dispatch> current = new ThreadLocal<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlayerCommandGate(
            AgentControlSession control,
            BiConsumer<String, Boolean> authorize,
            Consumer<JsonObject> observeSubmission) {
        this.control = control;
        this.authorize = authorize;
        this.observeSubmission = observeSubmission;
    }

    public Dispatch enter(
            AgentControlSession.Snapshot accepted, InputDispatchContext.Permit permit) {
        Dispatch result = new Dispatch(accepted, permit, current.get());
        current.set(result);
        return result;
    }

    public boolean requiresInspection(boolean agentRouted) {
        return agentRouted || current.get() != null || control.snapshot().agentControlled();
    }

    public boolean allow(
            String command,
            boolean agentRouted,
            boolean packet,
            Supplier<PlayerCommandPolicy.Parsed> nativeParse) {
        return allow(command, agentRouted, packet, nativeParse, null);
    }

    public boolean allowConfiguration(
            String command,
            boolean agentRouted,
            ConfigurationKind kind,
            Supplier<PlayerCommandPolicy.Parsed> nativeParse) {
        java.util.Objects.requireNonNull(kind);
        return allow(command, agentRouted, true, nativeParse, kind);
    }

    private boolean allow(
            String command,
            boolean agentRouted,
            boolean packet,
            Supplier<PlayerCommandPolicy.Parsed> nativeParse,
            ConfigurationKind configuration) {
        if (!requiresInspection(agentRouted)) return true;
        Dispatch dispatch = current.get();
        try {
            if (closed.get() || dispatch == null)
                throw denied("PLAYER_COMMAND_ORIGIN_UNAVAILABLE", "No active Agent input dispatch");
            if (dispatch.failure != null) return false;
            if (dispatch.permit == null)
                throw denied(
                        "PLAYER_COMMAND_ORIGIN_UNAVAILABLE", "No originating input effect permit");
            dispatch.permit.check();
            control.requireVersion(
                    dispatch.accepted.controlSessionId(), dispatch.accepted.transitionSequence());
            if (!control.snapshot().agentControlled())
                throw denied("TAKEOVER_REQUIRED", "Player commands require TAKEOVER");
            if (!agentRouted)
                throw denied(
                        "PLAYER_COMMAND_ORIGIN_UNAVAILABLE",
                        "Unknown command origin during TAKEOVER");
            authorize.accept(dispatch.permit.gestureId(), true);
            boolean nativeParameters = configuration != null && configuration.fixedEffect != null;
            if (nativeParameters && !"".equals(command))
                throw denied(
                        "INVALID_PLAYER_COMMAND", "Native parameter action is not command text");
            boolean emptyConfiguration =
                    (configuration == ConfigurationKind.COMMAND_BLOCK
                                    || configuration == ConfigurationKind.COMMAND_MINECART
                                    || nativeParameters)
                            && command != null
                            && command.isBlank();
            if (emptyConfiguration) {
                if (command.length() > 2048
                        || command.codePoints().anyMatch(Character::isISOControl))
                    throw denied("INVALID_PLAYER_COMMAND", "Invalid bounded command configuration");
            } else PlayerCommandPolicy.validateText(command);
            String expected = dispatch.permit.expectedPlayerCommand();
            if (expected != null
                    && (configuration != null
                            || !expected.equals(command)
                            || (packet && !dispatch.permit.playerCommandSubmissionAllowed())))
                throw denied(
                        "PLAYER_COMMAND_CONTEXT_CHANGED",
                        "Helper command changed or submitted before native Enter");
            var parsed = emptyConfiguration ? null : nativeParse.get();
            if (!emptyConfiguration && parsed == null)
                throw denied(
                        "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                        "Native command parse is unavailable");
            if (parsed != null && !parsed.complete())
                throw denied(
                        "PLAYER_COMMAND_NOT_AVAILABLE",
                        "Command is not complete in the current native player tree");
            var effect =
                    nativeParameters
                            ? configuration.fixedEffect
                            : parsed == null ? null : PlayerCommandPolicy.classify(parsed);
            if (configuration == ConfigurationKind.JIGSAW_BLOCK
                    && effect != PlayerCommandPolicy.Effect.STATE_MUTATION
                    && effect != PlayerCommandPolicy.Effect.CONTROL_PLANE)
                effect = PlayerCommandPolicy.Effect.UNRESOLVED;
            if (effect == PlayerCommandPolicy.Effect.CONTROL_PLANE)
                throw denied(
                        "PLAYER_COMMAND_OUT_OF_SCOPE",
                        configuration == ConfigurationKind.STRUCTURE_SAVE
                                ? "Structure file saving requires a separate storage-mutation policy"
                                : configuration == ConfigurationKind.TEST_BLOCK
                                                || configuration == ConfigurationKind.TEST_INSTANCE
                                        ? "Test management configuration and actions are outside the approved world-operation policy"
                                        : "Server/host control-plane commands are not world operations");
            if (effect == PlayerCommandPolicy.Effect.UNRESOLVED)
                throw denied(
                        "PLAYER_COMMAND_UNCLASSIFIED",
                        configuration == ConfigurationKind.JIGSAW_GENERATE
                                ? "Jigsaw generation requires trusted server template effects; configuration may already have been submitted"
                                : configuration == ConfigurationKind.STRUCTURE_LOAD
                                        ? "Structure loading requires trusted server template effects; no configuration packet was admitted"
                                        : "Unknown or indirect command effects cannot be safely classified");
            if (packet) {
                // Native parsing/encoding can be expensive; cancellation and mode loss still win
                // before publication.
                dispatch.permit.check();
                control.requireVersion(
                        dispatch.accepted.controlSessionId(),
                        dispatch.accepted.transitionSequence());
                authorize.accept(dispatch.permit.gestureId(), true);
                if (++dispatch.packets > 16)
                    throw denied(
                            "PLAYER_COMMAND_BUDGET_EXCEEDED",
                            "Too many commands in one native dispatch");
                JsonObject receipt =
                        configuration == null
                                ? PlayerCommandEvidence.classified(
                                        new JsonObject(), effect, "GAME_ROUTED_RAW")
                                : PlayerCommandEvidence.configuration(configuration, effect);
                receipt.addProperty("gestureId", dispatch.permit.gestureId());
                observeSubmission.accept(receipt.deepCopy());
                dispatch.receipt = receipt;
            }
            return true;
        } catch (ProtocolState.ProtocolException failure) {
            if (dispatch != null) dispatch.failure = failure;
            return false;
        } catch (AgentControlSession.ModeException failure) {
            if (dispatch != null) dispatch.failure = denied(failure.code(), failure.getMessage());
            return false;
        } catch (RuntimeException failure) {
            if (dispatch != null)
                dispatch.failure =
                        denied(
                                "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                                "Native command context could not be validated");
            return false;
        }
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private static ProtocolState.ProtocolException denied(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }

    public final class Dispatch implements AutoCloseable {
        private final AgentControlSession.Snapshot accepted;
        private final InputDispatchContext.Permit permit;
        private final Dispatch parent;
        private JsonObject receipt;
        private ProtocolState.ProtocolException failure;
        private int packets;

        private Dispatch(
                AgentControlSession.Snapshot accepted,
                InputDispatchContext.Permit permit,
                Dispatch parent) {
            this.accepted = accepted;
            this.permit = permit;
            this.parent = parent;
        }

        public void check() {
            if (failure != null) throw failure;
        }

        public String gestureId() {
            if (permit == null)
                throw denied("PLAYER_COMMAND_ORIGIN_UNAVAILABLE", "No input effect permit");
            permit.check();
            return permit.gestureId();
        }

        public void checkEffect() {
            check();
            if (permit != null) permit.check();
        }

        public JsonObject receipt() {
            check();
            if (receipt == null)
                throw denied(
                        "PLAYER_COMMAND_NOT_SUBMITTED",
                        "No validated native command packet was submitted");
            return receipt.deepCopy();
        }

        @Override
        public void close() {
            if (parent == null) current.remove();
            else current.set(parent);
        }
    }

    public Dispatch dispatch() {
        return current.get();
    }

    public boolean reject(String code, String message) {
        Dispatch dispatch = current.get();
        if (dispatch != null) dispatch.failure = denied(code, message);
        return false;
    }
}
