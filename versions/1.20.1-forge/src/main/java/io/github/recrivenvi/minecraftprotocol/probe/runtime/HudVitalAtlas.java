package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;

/** 1.20.1's native icons.png tiles; these names describe drawn tiles, never player values. */
public final class HudVitalAtlas {
    private HudVitalAtlas() {}

    public static String label(UiHudIcons.Kind kind, int u, int v) {
        return switch (kind) {
            case HEALTH -> heart(u, v);
            case ARMOR ->
                    v != 9
                            ? null
                            : switch (u) {
                                case 16 -> "hud/armor_empty";
                                case 25 -> "hud/armor_half";
                                case 34 -> "hud/armor_full";
                                default -> null;
                            };
            case FOOD ->
                    v != 27
                            ? null
                            : switch (u) {
                                case 16 -> "hud/food_empty";
                                case 52 -> "hud/food_full";
                                case 61 -> "hud/food_half";
                                case 133 -> "hud/food_empty_hunger";
                                case 88 -> "hud/food_full_hunger";
                                case 97 -> "hud/food_half_hunger";
                                default -> null;
                            };
            case AIR ->
                    v != 18
                            ? null
                            : switch (u) {
                                case 16 -> "hud/air";
                                case 25 -> "hud/air_bursting";
                                default -> null;
                            };
            case MOUNT_HEALTH ->
                    v != 9
                            ? null
                            : switch (u) {
                                case 52 -> "hud/heart/vehicle_container";
                                case 88 -> "hud/heart/vehicle_full";
                                case 97 -> "hud/heart/vehicle_half";
                                default -> null;
                            };
        };
    }

    private static String heart(int u, int v) {
        if ((v != 0 && v != 45) || u < 16 || (u - 16) % 9 != 0) return null;
        int tile = (u - 16) / 9;
        String hardcore = v == 45 ? "hardcore_" : "";
        if (tile <= 1)
            return "hud/heart/container"
                    + (v == 45 ? "_hardcore" : "")
                    + (tile == 1 ? "_blinking" : "");
        String theme;
        int offset;
        boolean blink;
        if (tile >= 4 && tile <= 7) {
            theme = "";
            offset = tile - 4;
            blink = true;
        } else if (tile >= 8 && tile <= 11) {
            theme = "poisoned_";
            offset = tile - 8;
            blink = true;
        } else if (tile >= 12 && tile <= 15) {
            theme = "withered_";
            offset = tile - 12;
            blink = true;
        } else if (tile >= 16 && tile <= 17) {
            theme = "absorbing_";
            offset = tile - 16;
            blink = false;
        } else if (tile >= 18 && tile <= 19) {
            theme = "frozen_";
            offset = tile - 18;
            blink = false;
        } else return null;
        return "hud/heart/"
                + theme
                + hardcore
                + (offset % 2 == 1 ? "half" : "full")
                + (blink && offset >= 2 ? "_blinking" : "");
    }
}
