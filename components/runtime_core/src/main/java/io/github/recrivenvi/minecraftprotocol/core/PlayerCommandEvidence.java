package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Classified input provenance is not a server execution acknowledgement. */
public final class PlayerCommandEvidence {
    interface Source {
        List<JsonObject> commandSubmissions();

        boolean commandSubmissionsComplete();
    }

    private PlayerCommandEvidence() {}

    public static JsonObject configuration(
            PlayerCommandGate.ConfigurationKind kind, PlayerCommandPolicy.Effect configuredEffect) {
        if (kind.payloadType == null
                || kind.fixedEffect != null && kind.fixedEffect != configuredEffect
                || kind == PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK
                        && configuredEffect != PlayerCommandPolicy.Effect.STATE_MUTATION
                || configuredEffect == PlayerCommandPolicy.Effect.CONTROL_PLANE
                || configuredEffect == PlayerCommandPolicy.Effect.UNRESOLVED)
            throw new IllegalArgumentException("Unsafe configuration cannot produce a receipt");
        JsonObject result =
                classified(
                        new JsonObject(),
                        PlayerCommandPolicy.Effect.STATE_MUTATION,
                        "GAME_ROUTED_RAW");
        result.addProperty("type", "command.player.configuration");
        result.addProperty("configurationKind", kind.name());
        result.addProperty("configurationPayloadType", kind.payloadType);
        result.addProperty(
                "configuredCommandEffect",
                configuredEffect == null ? "NONE" : configuredEffect.name());
        result.addProperty("classificationSource", "native_player_command_configuration");
        result.addProperty("contaminationReason", "player_command_configuration");
        return result;
    }

    public static JsonObject classified(
            JsonObject base, PlayerCommandPolicy.Effect effect, String mechanism) {
        if (effect == PlayerCommandPolicy.Effect.UNRESOLVED
                || effect == PlayerCommandPolicy.Effect.CONTROL_PLANE)
            throw new IllegalArgumentException("Denied effects cannot produce an accepted receipt");
        boolean mutation = effect == PlayerCommandPolicy.Effect.STATE_MUTATION;
        boolean contaminated = mutation || !mechanism.equals("GAME_ROUTED_RAW");
        base.addProperty("type", "command.player.submission");
        base.addProperty("accepted", true);
        base.addProperty("perspective", "current_player");
        base.addProperty("mode", mutation ? "DEBUG_PRIVILEGED" : "PLAYTEST");
        base.addProperty("mechanism", mechanism);
        base.addProperty("normalPacket", true);
        base.addProperty("serverValidation", true);
        base.addProperty("permissionEscalated", false);
        base.addProperty("effectClassification", effect.name());
        base.addProperty("classificationSource", "native_player_command_tree");
        base.addProperty("effectConfirmed", false);
        base.addProperty("serverValidationObserved", false);
        base.addProperty("gameplayAcceptanceEligible", false);
        base.addProperty("evidenceContaminated", contaminated);
        base.addProperty(
                "contaminationReason",
                mutation
                        ? "player_command_state_mutation"
                        : contaminated ? "non_game_routed_player_command" : "none");
        base.addProperty("submissionStatus", "submitted_unconfirmed");
        return base;
    }

    static void attach(JsonObject result, List<JsonObject> receipts) {
        if (receipts.isEmpty()) return;
        JsonArray values = new JsonArray();
        receipts.forEach(receipt -> values.add(receipt.deepCopy()));
        result.add("commandSubmissions", values);
        boolean mutation =
                receipts.stream()
                        .anyMatch(
                                r ->
                                        r.get("effectClassification")
                                                .getAsString()
                                                .equals("STATE_MUTATION"));
        boolean contaminated =
                receipts.stream().anyMatch(r -> r.get("evidenceContaminated").getAsBoolean())
                        || result.has("evidenceContaminated")
                                && result.get("evidenceContaminated").getAsBoolean();
        result.addProperty("evidenceContaminated", contaminated);
        if (mutation || !result.has("evidenceAuthority"))
            result.addProperty("evidenceAuthority", mutation ? "DEBUG_PRIVILEGED" : "PLAYTEST");
        result.addProperty("commandEffectsConfirmed", false);
    }
}
