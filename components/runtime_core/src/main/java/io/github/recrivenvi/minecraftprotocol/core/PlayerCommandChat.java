package io.github.recrivenvi.minecraftprotocol.core;

import java.lang.ref.WeakReference;

/** Owner-thread guard for a single native Chat key/character/Enter gesture. */
public final class PlayerCommandChat {
    public record Frame(
            Object world,
            Object player,
            Object connection,
            Object screen,
            Object input,
            boolean chat,
            String text,
            boolean focused,
            int cursor,
            String selected) {}

    private WeakReference<Object> world = new WeakReference<>(null);
    private WeakReference<Object> player = new WeakReference<>(null);
    private WeakReference<Object> connection = new WeakReference<>(null);
    private WeakReference<Object> screen = new WeakReference<>(null);
    private WeakReference<Object> input = new WeakReference<>(null);
    private String gesture;
    private String command;
    private String draft;

    public void prepare(Frame frame, String command, String gesture, int nativeLimit) {
        clear();
        PlayerCommandPolicy.validateText(command);
        if (command.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF))
            throw error("INVALID_PLAYER_COMMAND", "Command contains an invalid Unicode scalar");
        if (command.length() + 1 > nativeLimit)
            throw error("PLAYER_COMMAND_CHAT_LIMIT", "Command exceeds the native chat input limit");
        if (frame.world() == null || frame.player() == null || frame.connection() == null)
            throw error("PLAYER_UNAVAILABLE", "A connected current player is required");
        if (frame.screen() != null)
            throw error(
                    "PLAYER_COMMAND_SCREEN_ACTIVE",
                    "Explicitly finish the current Screen before opening command chat");
        this.world = new WeakReference<>(frame.world());
        this.player = new WeakReference<>(frame.player());
        this.connection = new WeakReference<>(frame.connection());
        this.command = command;
        this.gesture = gesture;
        this.draft = "";
    }

    public void beforeOpen(Frame frame, String command, String gesture) {
        requireWorld(frame, command, gesture);
        if (frame.screen() != null)
            throw error(
                    "PLAYER_COMMAND_CONTEXT_CHANGED", "Screen changed before Chat key dispatch");
    }

    public boolean ready(Frame frame, String command, String gesture) {
        requireWorld(frame, command, gesture);
        if (frame.screen() == null && screen.get() == null) return false;
        if (!frame.chat() || frame.input() == null)
            throw error(
                    "PLAYER_COMMAND_CONTEXT_CHANGED",
                    "Native Chat input was replaced or unavailable");
        if (screen.get() == null) {
            screen = new WeakReference<>(frame.screen());
            input = new WeakReference<>(frame.input());
        }
        requireDraft(frame, command, gesture);
        return true;
    }

    public void requireDraft(Frame frame, String command, String gesture) {
        requireWorld(frame, command, gesture);
        if (!frame.chat()
                || screen.get() == null
                || screen.get() != frame.screen()
                || input.get() == null
                || input.get() != frame.input()
                || !frame.focused()
                || !draft.equals(frame.text())
                || frame.cursor() != draft.length()
                || !frame.selected().isEmpty())
            throw error(
                    "PLAYER_COMMAND_CONTEXT_CHANGED",
                    "Command Screen, focus, draft or selection changed; no submit");
    }

    public String nextChunk(Frame frame, String command, String gesture) {
        requireDraft(frame, command, gesture);
        String remaining = ("/" + command).substring(draft.length());
        return remaining.substring(
                0,
                remaining.offsetByCodePoints(
                        0, Math.min(32, remaining.codePointCount(0, remaining.length()))));
    }

    public void acceptCharacter(Frame frame, String command, String gesture, int codePoint) {
        draft += new String(Character.toChars(codePoint));
        requireDraft(frame, command, gesture);
    }

    public void beforeSubmit(Frame frame, String command, String gesture) {
        requireDraft(frame, command, gesture);
        if (!("/" + command).equals(draft))
            throw error("PLAYER_COMMAND_CONTEXT_CHANGED", "Command typing is not complete");
    }

    public void clear() {
        world.clear();
        player.clear();
        connection.clear();
        screen.clear();
        input.clear();
        gesture = null;
        command = null;
        draft = null;
    }

    public void requireWorld(Frame frame, String command, String gesture) {
        if (!matchesWorld(frame.world(), frame.player(), frame.connection(), command, gesture))
            throw error(
                    "PLAYER_COMMAND_CONTEXT_CHANGED", "Command gesture or connected world changed");
    }

    public boolean matchesWorld(
            Object world, Object player, Object connection, String command, String gesture) {
        return !(this.command == null
                || !this.command.equals(command)
                || !this.gesture.equals(gesture)
                || this.world.get() == null
                || this.world.get() != world
                || this.player.get() == null
                || this.player.get() != player
                || this.connection.get() == null
                || this.connection.get() != connection);
    }

    private static ProtocolState.ProtocolException error(String code, String message) {
        return new ProtocolState.ProtocolException(code, 409, message);
    }
}
