package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import org.junit.jupiter.api.Test;

class HudVitalAtlasTest {
    @Test
    void everyActualNativeHeartVariantMapsWithoutReadingPlayerStatistics() throws Exception {
        Class<?> type = Class.forName("net.minecraft.client.gui.Gui$HeartType");
        var getX = type.getDeclaredMethod("getX", boolean.class, boolean.class);
        getX.setAccessible(true);
        for (Object value : type.getEnumConstants()) {
            String name = ((Enum<?>) value).name();
            String theme =
                    switch (name) {
                        case "CONTAINER", "NORMAL" -> "";
                        case "POISIONED" -> "poisoned_";
                        case "WITHERED" -> "withered_";
                        case "ABSORBING" -> "absorbing_";
                        case "FROZEN" -> "frozen_";
                        default -> throw new AssertionError(name);
                    };
            for (boolean half : new boolean[] {false, true})
                for (boolean blink : new boolean[] {false, true})
                    for (int y : new int[] {0, 45}) {
                        int x = (Integer) getX.invoke(value, half, blink);
                        String label = HudVitalAtlas.label(UiHudIcons.Kind.HEALTH, x, y);
                        assertNotNull(label, name + ":" + half + ":" + blink + ":" + y);
                        if (name.equals("CONTAINER"))
                            assertTrue(label.startsWith("hud/heart/container"));
                        else
                            assertTrue(
                                    label.startsWith(
                                            "hud/heart/"
                                                    + theme
                                                    + (y == 45 ? "hardcore_" : "")
                                                    + (half ? "half" : "full")));
                    }
        }
    }

    @Test
    void onlyKnownNativeTilesAreSemanticGlyphs() {
        assertEquals("hud/food_empty_hunger", HudVitalAtlas.label(UiHudIcons.Kind.FOOD, 133, 27));
        assertEquals("hud/armor_half", HudVitalAtlas.label(UiHudIcons.Kind.ARMOR, 25, 9));
        assertEquals("hud/air_bursting", HudVitalAtlas.label(UiHudIcons.Kind.AIR, 25, 18));
        assertEquals(
                "hud/heart/vehicle_half", HudVitalAtlas.label(UiHudIcons.Kind.MOUNT_HEALTH, 97, 9));
        assertNull(HudVitalAtlas.label(UiHudIcons.Kind.HEALTH, 17, 0));
        assertNull(HudVitalAtlas.label(UiHudIcons.Kind.HEALTH, 52, 9));
        assertNull(HudVitalAtlas.label(UiHudIcons.Kind.FOOD, 52, 18));
    }
}
