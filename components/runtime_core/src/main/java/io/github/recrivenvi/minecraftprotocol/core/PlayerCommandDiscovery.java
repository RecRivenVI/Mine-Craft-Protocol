package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;

/**
 * Bounded projection of the current player's native command discovery, never an execution probe.
 */
public final class PlayerCommandDiscovery {
    public record Query(String input, int cursor, int maxResults) {
        public Query {
            if (input == null
                    || input.length() > 2048
                    || input.codePoints().anyMatch(Character::isISOControl)
                    || input.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)
                    || cursor < 0
                    || cursor > input.length()
                    || cursor > 0
                            && cursor < input.length()
                            && Character.isHighSurrogate(input.charAt(cursor - 1))
                            && Character.isLowSurrogate(input.charAt(cursor)))
                throw new ProtocolState.ProtocolException(
                        "INVALID_COMMAND_DISCOVERY",
                        400,
                        "Discovery requires bounded text and a valid cursor");
            if (maxResults < 1 || maxResults > 128)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                        413,
                        "Discovery result limit must be 1..128");
        }

        public int inputOffset() {
            return input.startsWith("/") ? 1 : 0;
        }

        public String commandInput() {
            return input.substring(inputOffset());
        }

        public int commandCursor() {
            return Math.max(0, cursor - inputOffset());
        }
    }

    public record Suggestion(int start, int end, String text, String tooltip) {
        public Suggestion {
            Objects.requireNonNull(text);
        }
    }

    private PlayerCommandDiscovery() {}

    public static JsonObject contract() {
        JsonObject result = new JsonObject();
        result.addProperty("path", "/v0/command/player");
        result.addProperty("method", "POST");
        result.addProperty("dataCategory", "USER");
        result.addProperty("defaultAction", "execute");
        result.addProperty("discoveryReadScopes", "read command");
        result.addProperty("discoveryModeIndependent", true);
        result.addProperty("serverCompletionSupported", true);
        result.addProperty("defaultCompletionSource", "CLIENT_LOCAL");
        result.addProperty("serverCompletionMaxInFlight", 1);
        result.addProperty(
                "serverCompletionMinimumIntervalMs",
                PlayerCommandSuggestionRequests.MIN_INTERVAL_MILLIS);
        result.addProperty("serverCompletionTtlMs", PlayerCommandSuggestionRequests.TIMEOUT_MILLIS);
        result.addProperty("serverCompletionHumanPriority", true);
        result.addProperty("serverCompletionBudgetSharedWithVanilla", true);
        result.addProperty("localCompletionDoesNotOwnHumanRequests", true);
        result.addProperty("batchModeRequirement", "OPERATE_OR_TAKEOVER");
        result.addProperty("maxBatchCommands", 32);
        result.addProperty("maxBatchCharacters", 32768);
        result.addProperty("batchRoute", "NATIVE");
        result.addProperty("batchRequiresInputLease", false);
        result.addProperty("serverExecutionAcknowledged", false);
        result.addProperty("grantsAuthority", false);
        return result;
    }

    public static JsonObject result(
            Query query,
            List<String> roots,
            int rootCount,
            List<Suggestion> suggestions,
            int suggestionCount,
            boolean parsedExecutable,
            PlayerCommandPolicy.Effect effect) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "command.player.discovery");
        result.addProperty("dataCategory", "USER");
        result.addProperty("perspective", "current_player");
        result.addProperty("source", "current_player_native_command_tree");
        result.addProperty("input", query.input());
        result.addProperty("cursor", query.cursor());
        result.addProperty("maxResults", query.maxResults());
        result.addProperty("requiresControlLease", false);
        result.addProperty("inputDispatched", false);
        result.addProperty("commandExecuted", false);
        result.addProperty("serverAcceptanceConfirmed", false);
        result.addProperty("availability", "client_received_tree_and_current_suggestions");
        result.addProperty("availabilityMayChange", true);
        result.addProperty("submissionRequiresIndependentAuthorization", true);
        result.addProperty("submissionModeRequirement", "OPERATE_OR_TAKEOVER");
        result.addProperty("parsedExecutable", parsedExecutable);
        boolean allowed =
                parsedExecutable
                        && effect != null
                        && effect != PlayerCommandPolicy.Effect.UNRESOLVED
                        && effect != PlayerCommandPolicy.Effect.CONTROL_PLANE;
        result.addProperty("effectPolicyAllowsSubmission", allowed);
        result.addProperty(
                "policyReason",
                !parsedExecutable
                        ? "native_parse_incomplete"
                        : effect == PlayerCommandPolicy.Effect.CONTROL_PLANE
                                ? "control_plane_out_of_scope"
                                : !allowed ? "unclassified_effect" : "supported_effect");
        if (effect != null) result.addProperty("effectClassification", effect.name());
        JsonArray rootValues = new JsonArray();
        roots.stream()
                .limit(query.maxResults())
                .forEach(
                        root -> {
                            if (root.length() > 2048)
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                                        413,
                                        "Native root name exceeds the text budget");
                            rootValues.add(root);
                        });
        result.add("roots", rootValues);
        result.addProperty("rootCount", rootCount);
        result.addProperty("rootsTruncated", rootCount > rootValues.size());
        JsonArray candidates = new JsonArray();
        suggestions.stream()
                .limit(query.maxResults())
                .forEach(
                        suggestion -> {
                            if (suggestion.start() < 0
                                    || suggestion.end() < suggestion.start()
                                    || suggestion.end() > query.input().length())
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_CONTEXT_UNAVAILABLE",
                                        409,
                                        "Invalid native suggestion range");
                            JsonObject value = new JsonObject();
                            if (suggestion.text().length() > 2048)
                                throw new ProtocolState.ProtocolException(
                                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                                        413,
                                        "Native completion exceeds the text budget");
                            value.addProperty("start", suggestion.start());
                            value.addProperty("end", suggestion.end());
                            value.addProperty("text", suggestion.text());
                            if (suggestion.tooltip() != null) {
                                value.addProperty("tooltip", bounded(suggestion.tooltip()));
                                value.addProperty(
                                        "tooltipTruncated", suggestion.tooltip().length() > 2048);
                            }
                            candidates.add(value);
                        });
        result.add("suggestions", candidates);
        result.addProperty("suggestionCount", suggestionCount);
        result.addProperty("suggestionsTruncated", suggestionCount > candidates.size());
        return result;
    }

    private static String bounded(String text) {
        int end = Math.min(text.length(), 2048);
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end - 1)))
            end--;
        return text.substring(0, end);
    }
}
