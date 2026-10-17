package io.github.recrivenvi.minecraftprotocol.probe.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.chat.Component;

/** Opt-in local UI fixtures, excluded from the released product. */
public final class ShowcaseToastFixture {
    private ShowcaseToastFixture() {}

    public static void showDebug() {
        Minecraft client = Minecraft.getInstance();
        var manager = client.gui.toastManager();
        manager.addToast(
                new TutorialToast(
                        client.font,
                        TutorialToast.Icons.MOUSE,
                        Component.literal("DEBUG progress fixture"),
                        Component.literal("local display only"),
                        true));
        manager.addToast(
                new TutorialToast(
                        client.font,
                        TutorialToast.Icons.MOUSE,
                        Component.literal("DEBUG static fixture"),
                        Component.literal("no progress bar"),
                        false));
        SystemToast.add(
                manager,
                new SystemToast.SystemToastId(60000),
                Component.literal("DEBUG system fixture"),
                Component.literal("local reset and dismissal"));
    }
}
