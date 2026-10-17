package io.github.recrivenvi.minecraftprotocol.probe.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;

/** Opt-in presentation fixture; never part of the product JAR or gameplay evidence. */
public final class ShowcaseToastFixture {
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
        SystemToast.add(
                manager,
                new SystemToast.SystemToastId(60000),
                Component.literal("DEBUG system fixture"),
                Component.literal("local reset and dismissal"));
    }

    public static void show() {
        Minecraft client = Minecraft.getInstance();
        var manager = client.getToasts();
        class FixtureTutorialToast extends TutorialToast {
            FixtureTutorialToast() {
                super(
                        TutorialToast.Icons.MOUSE,
                        Component.literal("Showcase Fixture"),
                        Component.literal("Tutorial Toast / not gameplay"),
                        false);
            }

            @Override
            public Toast.Visibility render(
                    GuiGraphics graphics, ToastComponent toasts, long elapsed) {
                if (elapsed >= 12000) hide();
                return super.render(graphics, toasts, elapsed);
            }
        }
        manager.addToast(new FixtureTutorialToast());
        SystemToast.add(
                manager,
                new SystemToast.SystemToastId(12000),
                Component.literal("Showcase Fixture"),
                Component.literal("System Toast / not an advancement"));
    }
}
