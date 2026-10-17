package io.github.recrivenvi.minecraftprotocol.core;

import java.util.List;
import java.util.Set;

/** Effect taxonomy over native routes and their separately parsed embedded programs. */
public final class PlayerCommandPolicy {
    public enum Effect {
        COMMUNICATION,
        INFORMATION,
        STATE_MUTATION,
        CONTROL_PLANE,
        UNRESOLVED
    }

    public record Node(String name, boolean literal) {}

    public record Frame(String root, List<Node> nodes) {
        public Frame {
            nodes = List.copyOf(nodes);
        }

        boolean literal(String name) {
            return nodes.stream().anyMatch(n -> n.literal() && n.name().equals(name));
        }
    }

    public record Parsed(boolean complete, List<Frame> frames, List<Effect> embeddedEffects) {
        public Parsed {
            frames = List.copyOf(frames);
            embeddedEffects = List.copyOf(embeddedEffects);
        }

        public Parsed(boolean complete, List<Frame> frames) {
            this(complete, frames, List.of());
        }
    }

    private static final Set<String> CONTROL =
            Set.of(
                    "stop",
                    "op",
                    "deop",
                    "ban",
                    "ban-ip",
                    "banlist",
                    "pardon",
                    "pardon-ip",
                    "kick",
                    "save-all",
                    "save-on",
                    "save-off",
                    "publish",
                    "reload",
                    "datapack",
                    "whitelist",
                    "perf",
                    "debug",
                    "jfr",
                    "tick",
                    "forceload",
                    "transfer",
                    "setidletimeout",
                    "test");
    private static final Set<String> COMMUNICATION =
            Set.of("help", "say", "msg", "tellraw", "me", "list");
    private static final Set<String> INFORMATION = Set.of("seed", "locate");
    private static final Set<String> MUTATION =
            Set.of(
                    "advancement",
                    "attribute",
                    "clear",
                    "damage",
                    "defaultgamemode",
                    "effect",
                    "enchant",
                    "fill",
                    "fillbiome",
                    "gamemode",
                    "give",
                    "item",
                    "kill",
                    "particle",
                    "playsound",
                    "random",
                    "recipe",
                    "return",
                    "ride",
                    "rotate",
                    "setblock",
                    "setworldspawn",
                    "spawnpoint",
                    "spectate",
                    "spreadplayers",
                    "stopsound",
                    "summon",
                    "teleport",
                    "title",
                    "trigger",
                    "weather",
                    "worldborder");

    private PlayerCommandPolicy() {}

    public static String normalize(String input) {
        String command = input == null ? "" : input.trim();
        if (command.startsWith("/")) command = command.substring(1);
        validateText(command);
        return command;
    }

    public static void validateText(String command) {
        if (command == null
                || command.isBlank()
                || command.length() > 2048
                || command.codePoints().anyMatch(Character::isISOControl))
            throw new ProtocolState.ProtocolException(
                    "INVALID_PLAYER_COMMAND",
                    400,
                    "Command requires one non-empty line of at most 2048 characters");
        int depth = 0, tokens = 1;
        char quote = 0;
        boolean escaped = false, whitespace = false;
        for (char c : command.toCharArray()) {
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            if (c == '{' || c == '[' || c == '(') depth++;
            if ((c == '}' || c == ']' || c == ')') && depth > 0) depth--;
            if (Character.isWhitespace(c) && !whitespace) tokens++;
            whitespace = Character.isWhitespace(c);
            if (depth > 32 || tokens > 128)
                throw new ProtocolState.ProtocolException(
                        "PLAYER_COMMAND_BUDGET_EXCEEDED",
                        413,
                        "Command exceeds bounded nesting or token budget");
        }
    }

    public static Effect classify(Parsed parsed) {
        if (!parsed.complete() || parsed.frames().isEmpty()) return Effect.UNRESOLVED;
        if (parsed.frames().size() > 16
                || parsed.frames().stream().mapToInt(f -> f.nodes().size()).sum() > 128
                || parsed.embeddedEffects().size() > 16)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BUDGET_EXCEEDED",
                    413,
                    "Native command route exceeds context/node budget");
        Effect result = Effect.COMMUNICATION;
        for (Frame frame : parsed.frames()) {
            Effect effect = classifyFrame(frame);
            if (effect == Effect.CONTROL_PLANE || effect == Effect.UNRESOLVED) return effect;
            if (effect.ordinal() > result.ordinal()) result = effect;
        }
        for (Effect effect : parsed.embeddedEffects()) {
            if (effect == Effect.CONTROL_PLANE || effect == Effect.UNRESOLVED) return effect;
            if (effect.ordinal() > result.ordinal()) result = effect;
        }
        return result;
    }

    private static Effect classifyFrame(Frame frame) {
        String root = frame.root();
        if (CONTROL.contains(root)) return Effect.CONTROL_PLANE;
        if (Set.of("function", "schedule", "clone", "place", "loot").contains(root))
            return Effect.UNRESOLVED;
        if (root.equals("item") && (frame.literal("from") || frame.literal("modify")))
            return Effect.UNRESOLVED;
        if (root.equals("execute")) {
            if (frame.literal("function")) return Effect.UNRESOLVED;
            return frame.literal("store") || frame.literal("summon")
                    ? Effect.STATE_MUTATION
                    : Effect.INFORMATION;
        }
        if (COMMUNICATION.contains(root)) return Effect.COMMUNICATION;
        if (INFORMATION.contains(root)) return Effect.INFORMATION;
        return switch (root) {
            case "data" -> frame.literal("get") ? Effect.INFORMATION : Effect.STATE_MUTATION;
            case "time", "experience" ->
                    frame.literal("query") ? Effect.INFORMATION : Effect.STATE_MUTATION;
            case "scoreboard", "team", "tag" ->
                    frame.literal("list") || frame.literal("get")
                            ? Effect.INFORMATION
                            : Effect.STATE_MUTATION;
            case "attribute", "worldborder" ->
                    frame.literal("get") ? Effect.INFORMATION : Effect.STATE_MUTATION;
            case "bossbar" ->
                    frame.literal("get") || frame.literal("list")
                            ? Effect.INFORMATION
                            : Effect.STATE_MUTATION;
            case "difficulty" ->
                    frame.nodes().size() == 1 ? Effect.INFORMATION : Effect.STATE_MUTATION;
            case "gamerule" ->
                    frame.nodes().size() == 2 ? Effect.INFORMATION : Effect.STATE_MUTATION;
            default -> MUTATION.contains(root) ? Effect.STATE_MUTATION : Effect.UNRESOLVED;
        };
    }
}
