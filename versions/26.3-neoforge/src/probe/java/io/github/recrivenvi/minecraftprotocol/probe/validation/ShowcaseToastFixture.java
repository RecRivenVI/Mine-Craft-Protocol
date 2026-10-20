package io.github.recrivenvi.minecraftprotocol.probe.validation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Opt-in presentation fixture; never part of the product JAR or gameplay evidence. */
public final class ShowcaseToastFixture {
    private static Music userMusicFixture;
    private static MusicToastDisplayState musicToastState;
    private static LifecycleScreen lifecycleOwner;
    private static TutorialToast lifecycleTutorial;
    private static final List<TutorialToast> lifecycleBlockers = new ArrayList<>();

    private ShowcaseToastFixture() {}

    public static void openLifecycle(Screen previous) {
        if (!Boolean.getBoolean("minecraft.protocol.toastDebugFixture"))
            throw new IllegalStateException("Toast lifecycle fixture requires explicit validation");
        Minecraft client = Minecraft.getInstance();
        lifecycleOwner = new LifecycleScreen(previous, client.level);
        client.gui.setScreen(lifecycleOwner);
    }

    private static ToastManager lifecycleManager() {
        Minecraft client = Minecraft.getInstance();
        if (lifecycleOwner == null
                || client.gui.screen() != lifecycleOwner
                || client.level != lifecycleOwner.world)
            throw new IllegalStateException(
                    "Only the current disposable lifecycle fixture owns these Toasts");
        return client.gui.toastManager();
    }

    private static TutorialToast tutorial(String title) {
        return new TutorialToast(
                Minecraft.getInstance().font,
                TutorialToast.Icons.MOUSE,
                Component.literal(title),
                Component.literal("Native display"),
                false);
    }

    private static void showLifecycle(String kind) {
        ToastManager manager = lifecycleManager();
        switch (kind) {
            case "System" ->
                    manager.addToast(
                            new SystemToast(
                                    new SystemToast.SystemToastId(5000),
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
                                                    AdvancementType.TASK,
                                                    true,
                                                    false,
                                                    false)
                                            .build(
                                                    Identifier.withDefaultNamespace(
                                                            "lifecycle_fixture"))));
            case "Recipe" ->
                    RecipeToast.addOrUpdate(
                            manager,
                            new ShapelessCraftingRecipeDisplay(
                                    List.of(new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG)),
                                    new SlotDisplay.ItemSlotDisplay(Items.OAK_PLANKS),
                                    new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
            default -> throw new IllegalArgumentException("Unknown lifecycle fixture kind");
        }
    }

    private static void queueBlockers() {
        ToastManager manager = lifecycleManager();
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
            minecraft.gui.setScreen(previous);
            lifecycleOwner = null;
        }
    }

    public static void showFriendUser() {
        Minecraft client = Minecraft.getInstance();
        client.gui
                .toastManager()
                .addToast(
                        new FriendToast(
                                client.font,
                                null,
                                Component.literal("Local friend display fixture"),
                                60000));
    }

    public static void showNowPlayingUser() {
        Minecraft client = Minecraft.getInstance();
        if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0)
            throw new IllegalStateException(
                    "NowPlaying fixture requires master volume to remain zero");
        clearNowPlayingUser();
        Music music = client.getSituationalMusic();
        if (music == null)
            throw new IllegalStateException(
                    "No native situational music is available for this disposable fixture");
        musicToastState = client.options.musicToast().get();
        userMusicFixture = music;
        try {
            client.getMusicManager().startPlaying(music);
            String resolved = client.getMusicManager().getCurrentMusicTranslationKey();
            if (resolved == null || resolved.isBlank())
                throw new IllegalStateException(
                        "Native music did not resolve a real song; USER NowPlaying text is unverified");
            var manager = client.gui.toastManager();
            manager.setMusicToastDisplayState(MusicToastDisplayState.PAUSE_AND_TOAST);
            manager.showNowPlayingToast();
        } catch (RuntimeException | Error failure) {
            try {
                clearNowPlayingUser();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public static void clearNowPlayingUser() {
        if (userMusicFixture == null) return;
        Minecraft client = Minecraft.getInstance();
        // This stops only the disposable fixture's native playlist, not a transaction restoring
        // a previous random song. No volume or persistent Options value is modified.
        try {
            client.getMusicManager().stopPlaying(userMusicFixture);
            client.gui.toastManager().hideNowPlayingToast();
        } finally {
            userMusicFixture = null;
            if (musicToastState != null)
                client.gui.toastManager().setMusicToastDisplayState(musicToastState);
            musicToastState = null;
        }
    }

    public static void showVisibilityDebug() {
        Minecraft client = Minecraft.getInstance();
        var manager = client.gui.toastManager();
        // The local display fixture never contacts any account/friends service.
        manager.addToast(
                new FriendToast(
                        client.font,
                        null,
                        Component.literal("DEBUG local friend display fixture"),
                        60000));
        // Only the manager's local display instance is prepared. Options and music stay unchanged.
        manager.setMusicToastDisplayState(MusicToastDisplayState.PAUSE_AND_TOAST);
        manager.showNowPlayingToast();
    }

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

    public static void show() {
        Minecraft client = Minecraft.getInstance();
        var manager = client.gui.toastManager();
        manager.addToast(
                new TutorialToast(
                        client.font,
                        TutorialToast.Icons.MOUSE,
                        Component.literal("Showcase Fixture"),
                        Component.literal("Tutorial Toast / not gameplay"),
                        false) {
                    @Override
                    public void update(ToastManager toasts, long elapsed) {
                        super.update(toasts, elapsed);
                        if (elapsed >= 12000) hide();
                    }
                });
        SystemToast.add(
                manager,
                new SystemToast.SystemToastId(12000),
                Component.literal("Showcase Fixture"),
                Component.literal("System Toast / not an advancement"));
    }
}
