package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded inspection of native-decoded mutation data; never parses raw command text or executes it.
 */
public final class PlayerCommandPayloads {
    public enum Kind {
        COMMAND,
        BLOCK_STATE
    }

    public record Program(Kind kind, String text) {}

    private final List<Program> programs = new ArrayList<>();
    private int nodes;

    public void capture(JsonElement value) {
        visit(value, 0);
    }

    public void command(String command) {
        program(Kind.COMMAND, command);
    }

    public void blockState(String state) {
        program(Kind.BLOCK_STATE, state);
    }

    public List<Program> programs() {
        return List.copyOf(programs);
    }

    public int nodes() {
        return nodes;
    }

    /** Field extraction only: syntax validity belongs to the native NbtPath argument parser. */
    public static boolean targetsCommand(String nativePath) {
        return pathField(nativePath).equals("Command");
    }

    public static String pathField(String nativePath) {
        String last = "";
        for (int i = 0; i < nativePath.length(); ) {
            char c = nativePath.charAt(i);
            if (c == '.') {
                i++;
                continue;
            }
            if (c == '[' || c == '{') {
                boolean indexed = c == '[';
                int depth = 0;
                char quote = 0;
                boolean escaped = false;
                do {
                    c = nativePath.charAt(i++);
                    if (quote != 0) {
                        if (escaped) escaped = false;
                        else if (c == '\\') escaped = true;
                        else if (c == quote) quote = 0;
                    } else if (c == '"' || c == '\'') quote = c;
                    else if (c == '[' || c == '{') depth++;
                    else if (c == ']' || c == '}') depth--;
                } while (i < nativePath.length() && depth > 0);
                if (indexed) last = "";
                continue;
            }
            var field = new StringBuilder();
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < nativePath.length()) {
                    c = nativePath.charAt(i++);
                    if (c == quote) break;
                    if (c == '\\' && i < nativePath.length()) c = nativePath.charAt(i++);
                    field.append(c);
                }
            } else {
                while (i < nativePath.length() && ".[{".indexOf(nativePath.charAt(i)) < 0)
                    field.append(nativePath.charAt(i++));
            }
            last = field.toString();
        }
        return last;
    }

    private void visit(JsonElement value, int depth) {
        if (++nodes > 512 || depth > 32) throw budget();
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) visit(child, depth + 1);
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                // This component is inert user data, not the native entity/block-entity carrier.
                if (entry.getKey().equals("minecraft:custom_data")) continue;
                JsonElement child = entry.getValue();
                if (child != null
                        && child.isJsonPrimitive()
                        && child.getAsJsonPrimitive().isString()) {
                    if (entry.getKey().equals("Command"))
                        program(Kind.COMMAND, child.getAsString());
                    if (entry.getKey().equals("final_state"))
                        program(Kind.BLOCK_STATE, child.getAsString());
                }
                visit(child, depth + 1);
            }
        }
    }

    private void program(Kind kind, String text) {
        if (text.isBlank()) return;
        if (text.length() > 2048 || programs.size() >= 16) throw budget();
        programs.add(new Program(kind, text));
    }

    private static ProtocolState.ProtocolException budget() {
        return new ProtocolState.ProtocolException(
                "PLAYER_COMMAND_BUDGET_EXCEEDED",
                413,
                "Native command payload exceeds inspection budget");
    }
}
