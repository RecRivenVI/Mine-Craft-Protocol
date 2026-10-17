package io.github.recrivenvi.minecraftprotocol.probe.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.chat.Component;

/** Opt-in local UI fixtures, excluded from the released product. */
public final class ShowcaseToastFixture {
    private static DebugSystemToast system;

    private ShowcaseToastFixture() {}

    public static void showDebug() {
        Minecraft client = Minecraft.getInstance();
        var manager = client.getToasts();
        manager.addToast(
                new TutorialToast(
                        TutorialToast.Icons.MOUSE,
                        Component.literal("DEBUG progress fixture"),
                        Component.literal("local display only"),
                        true));
        manager.addToast(
                new TutorialToast(
                        TutorialToast.Icons.MOUSE,
                        Component.literal("DEBUG static fixture"),
                        Component.literal("no progress bar"),
                        false));
        // This enum-based native version has a fixed display lifetime and no forceHide.
        // Hold only this opt-in probe Toast until its fixture cleanup is requested.
        system = new DebugSystemToast();
        manager.addToast(system);
    }

    public static void endDebug() {
        if (system != null) system.hold = false;
    }

    private static final class DebugSystemToast extends SystemToast {
        private boolean hold = true;

        DebugSystemToast() {
            super(
                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("DEBUG system fixture"),
                    Component.literal("local reset and dismissal"));
        }

        @Override
        public Toast.Visibility render(
                net.minecraft.client.gui.GuiGraphics graphics,
                ToastComponent toasts,
                long elapsed) {
            return super.render(graphics, toasts, hold ? 0 : elapsed + 10000);
        }
    }
}
