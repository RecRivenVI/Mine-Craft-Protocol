package io.github.recrivenvi.minecraftprotocol.probe.validation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Opt-in local UI fixtures, excluded from the released product. */
public final class ShowcaseToastFixture {
    private static DebugSystemToast system;

    private static LifecycleScreen lifecycleOwner;
    private static TutorialToast lifecycleTutorial;
    private static final List<TutorialToast> lifecycleBlockers = new ArrayList<>();

    private ShowcaseToastFixture() {}

    public static void openLifecycle(Screen previous) {
        if (!Boolean.getBoolean("minecraft.protocol.toastDebugFixture"))
            throw new IllegalStateException("Toast lifecycle fixture requires explicit validation");
        Minecraft client = Minecraft.getInstance();
        lifecycleOwner = new LifecycleScreen(previous, client.level);
        client.setScreen(lifecycleOwner);
    }

    private static ToastComponent lifecycleManager() {
        Minecraft client = Minecraft.getInstance();
        if (lifecycleOwner == null
                || client.screen != lifecycleOwner
                || client.level != lifecycleOwner.world)
            throw new IllegalStateException(
                    "Only the current disposable lifecycle fixture owns these Toasts");
        return client.getToasts();
    }

    private static TutorialToast tutorial(String title) {
        return new TutorialToast(
                TutorialToast.Icons.MOUSE,
                Component.literal(title),
                Component.literal("Native display"),
                false);
    }

    private static void showLifecycle(String kind) {
        ToastComponent manager = lifecycleManager();
        switch (kind) {
            case "System" ->
                    manager.addToast(
                            new SystemToast(
                                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                                    Component.literal("Lifecycle system fixture"),
                                    Component.literal("Native display only")));
            case "Tutorial" -> {
                lifecycleTutorial = tutorial("Lifecycle tutorial fixture");
                manager.addToast(lifecycleTutorial);
            }
            case "Advancement" ->
                    manager.addToast(
                            new AdvancementToast(
                                    Advancement.Builder.advancement()
                                            .display(
                                                    Items.DIAMOND,
                                                    Component.literal(
                                                            "Lifecycle advancement fixture"),
                                                    Component.literal("Native display only"),
                                                    null,
                                                    FrameType.TASK,
                                                    true,
                                                    false,
                                                    false)
                                            .build(
                                                    new ResourceLocation(
                                                            "minecraft", "lifecycle_fixture"))));
            case "Recipe" -> {
                Minecraft client = Minecraft.getInstance();
                if (client.level == null)
                    throw new IllegalStateException(
                            "Native recipe display requires the disposable client world");
                manager.addToast(
                        new RecipeToast(
                                client.level
                                        .getRecipeManager()
                                        .byKey(new ResourceLocation("minecraft", "oak_planks"))
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "Native oak planks recipe is unavailable"))));
            }
            default -> throw new IllegalArgumentException("Unknown lifecycle fixture kind");
        }
    }

    private static void queueBlockers() {
        ToastComponent manager = lifecycleManager();
        if (!lifecycleBlockers.isEmpty())
            throw new IllegalStateException("Queue blockers already owned");
        for (int i = 1; i <= 5; i++) {
            TutorialToast toast = tutorial("Queue blocker " + i);
            lifecycleBlockers.add(toast);
            manager.addToast(toast);
        }
    }

    private static void finishTutorial() {
        lifecycleManager();
        if (lifecycleTutorial == null)
            throw new IllegalStateException("No lifecycle tutorial is owned");
        lifecycleTutorial.hide();
    }

    private static void releaseBlockers() {
        lifecycleManager();
        for (TutorialToast toast : lifecycleBlockers) toast.hide();
        lifecycleBlockers.clear();
    }

    private static void clearLifecycle() {
        // Explicit reset of this opt-in disposable display fixture, never a product capability.
        lifecycleManager().clear();
        lifecycleTutorial = null;
        lifecycleBlockers.clear();
    }

    private static final class LifecycleScreen extends Screen {
        private final Screen previous;
        private final Object world;

        private LifecycleScreen(Screen previous, Object world) {
            super(Component.literal("Toast Lifecycle (Probe Fixture)"));
            this.previous = previous;
            this.world = world;
        }

        @Override
        protected void init() {
            String[] labels = {
                "System Lifecycle",
                "Tutorial Lifecycle",
                "Advancement Lifecycle",
                "Recipe Lifecycle",
                "Finish Tutorial",
                "Queue Blockers",
                "Queue Recipe",
                "Release Blockers",
                "Clear Lifecycle Toasts",
                "Done"
            };
            for (int i = 0; i < labels.length; i++) {
                final int action = i;
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(labels[i]),
                                        button -> {
                                            if (action < 4)
                                                showLifecycle(
                                                        new String[] {
                                                                    "System",
                                                                    "Tutorial",
                                                                    "Advancement",
                                                                    "Recipe"
                                                                }
                                                                [action]);
                                            else if (action == 4) finishTutorial();
                                            else if (action == 5) queueBlockers();
                                            else if (action == 6) showLifecycle("Recipe");
                                            else if (action == 7) releaseBlockers();
                                            else if (action == 8) clearLifecycle();
                                            else onClose();
                                        })
                                .bounds(width / 2 - 155 + (i % 2) * 160, 25 + (i / 2) * 24, 150, 20)
                                .build());
            }
        }

        @Override
        public void onClose() {
            clearLifecycle();
            minecraft.setScreen(previous);
            lifecycleOwner = null;
        }
    }

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
