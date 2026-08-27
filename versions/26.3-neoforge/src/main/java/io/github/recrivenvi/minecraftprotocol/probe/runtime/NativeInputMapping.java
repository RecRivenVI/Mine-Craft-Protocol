package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLScancode;

/** Preserve the V0 GLFW-numbered wire contract while Vanilla consumes SDL scancodes. */
public final class NativeInputMapping {
    private NativeInputMapping() {}

    public static int key(int wire) {
        if (wire >= 65 && wire <= 90) return InputConstants.KEY_A + wire - 65;
        if (wire >= 49 && wire <= 57) return InputConstants.KEY_1 + wire - 49;
        if (wire >= 290 && wire <= 301) return InputConstants.KEY_F1 + wire - 290;
        if (wire >= 302 && wire <= 313) return InputConstants.KEY_F13 + wire - 302;
        if (wire >= 321 && wire <= 329) return InputConstants.KEY_NUMPAD1 + wire - 321;
        return switch (wire) {
            case 32 -> InputConstants.KEY_SPACE;
            case 39 -> InputConstants.KEY_APOSTROPHE;
            case 44 -> InputConstants.KEY_COMMA;
            case 45 -> InputConstants.KEY_MINUS;
            case 46 -> InputConstants.KEY_PERIOD;
            case 47 -> InputConstants.KEY_SLASH;
            case 48 -> InputConstants.KEY_0;
            case 59 -> InputConstants.KEY_SEMICOLON;
            case 61 -> InputConstants.KEY_EQUALS;
            case 91 -> InputConstants.KEY_LBRACKET;
            case 92 -> InputConstants.KEY_BACKSLASH;
            case 93 -> InputConstants.KEY_RBRACKET;
            case 96 -> InputConstants.KEY_GRAVE;
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP;
            case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME;
            case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK;
            case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK;
            case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE;
            case 320 -> InputConstants.KEY_NUMPAD0;
            case 330 -> SDLScancode.SDL_SCANCODE_KP_PERIOD;
            case 331 -> SDLScancode.SDL_SCANCODE_KP_DIVIDE;
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> SDLScancode.SDL_SCANCODE_KP_MINUS;
            case 334 -> InputConstants.KEY_ADD;
            case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case 340 -> InputConstants.KEY_LSHIFT;
            case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT;
            case 343 -> InputConstants.KEY_LGUI;
            case 344 -> InputConstants.KEY_RSHIFT;
            case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT;
            case 347 -> InputConstants.KEY_RGUI;
            case 348 -> SDLScancode.SDL_SCANCODE_APPLICATION;
            default ->
                    throw new ProtocolState.ProtocolException(
                            "INVALID_KEY", 400, "Unsupported wire key: " + wire);
        };
    }

    public static int modifiers(int wire) {
        return ((wire & 1) != 0 ? InputConstants.MOD_SHIFT : 0)
                | ((wire & 2) != 0 ? InputConstants.MOD_CONTROL : 0)
                | ((wire & 4) != 0 ? InputConstants.MOD_ALT : 0)
                | ((wire & 8) != 0 ? InputConstants.MOD_SUPER : 0)
                | ((wire & 16) != 0 ? InputConstants.MOD_CAPS_LOCK : 0)
                | ((wire & 32) != 0 ? InputConstants.MOD_NUM_LOCK : 0);
    }

    public static KeyEvent event(int wireKey, int wireModifiers) {
        int scancode = key(wireKey), mods = modifiers(wireModifiers);
        return new KeyEvent(
                scancode, SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) mods, false), mods);
    }

    public static int button(int wire) {
        return switch (wire) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> wire + 1;
        };
    }
}
