package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLScancode;

final class NativeInputMappingTest {
    @Test
    void wireKeysKeepTheirMeaningAcrossTheSdlMigration() {
        assertEquals(InputConstants.KEY_E, NativeInputMapping.key(69));
        assertEquals(InputConstants.KEY_W, NativeInputMapping.key(87));
        assertEquals(InputConstants.KEY_ESCAPE, NativeInputMapping.key(256));
        assertEquals(InputConstants.KEY_F12, NativeInputMapping.key(301));
        assertEquals(InputConstants.KEY_NUMPAD9, NativeInputMapping.key(329));
        assertEquals(SDLScancode.SDL_SCANCODE_KP_PERIOD, NativeInputMapping.key(330));
        assertEquals(SDLScancode.SDL_SCANCODE_KP_DIVIDE, NativeInputMapping.key(331));
        assertNotEquals(NativeInputMapping.key(331), NativeInputMapping.key(47));
        assertNotEquals(NativeInputMapping.key(333), NativeInputMapping.key(45));
        assertThrows(ProtocolState.ProtocolException.class, () -> NativeInputMapping.key(-2));
    }

    @Test
    void buttonAndModifierNumbersAreTranslatedNotPassedThrough() {
        assertEquals(InputConstants.MOUSE_BUTTON_LEFT, NativeInputMapping.button(0));
        assertEquals(InputConstants.MOUSE_BUTTON_RIGHT, NativeInputMapping.button(1));
        assertEquals(InputConstants.MOUSE_BUTTON_MIDDLE, NativeInputMapping.button(2));
        assertEquals(
                InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL,
                NativeInputMapping.modifiers(3));
        assertEquals(
                InputConstants.MOD_ALT | InputConstants.MOD_SUPER,
                NativeInputMapping.modifiers(12));
        assertEquals(0, NativeInputMapping.modifiers(0));
    }
}
