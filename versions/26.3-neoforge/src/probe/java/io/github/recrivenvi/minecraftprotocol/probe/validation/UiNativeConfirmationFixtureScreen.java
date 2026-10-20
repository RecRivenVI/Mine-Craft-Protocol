package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen;
import net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;

/** Diagnostic Arrange of native UI only; no real fault, external service or user world is used. */
public final class UiNativeConfirmationFixtureScreen extends Screen {
    private final Screen previous;
    private final String fixtureId = UUID.randomUUID().toString();
    private int sequence;
    private String activeCase = "", nativeClass = "", callbackAction = "none";
    private boolean returned;
    private PackRepository draftPacks;
    private List<String> selectedPacksBefore = List.of();
    private int experimentOutputCalls;

    public UiNativeConfirmationFixtureScreen(Screen previous) {
        super(Component.literal("More Native Screens (Fixture)"));
        if (!UiNavigationFixtureScreen.enabled())
            throw new IllegalStateException(
                    "Native screen fixtures require explicit UI validation");
        this.previous = previous;
    }

    @Override
    protected void init() {
        returned = !activeCase.isEmpty() && minecraft.gui.screen() == this;
        List<String> labels =
                List.of(
                        "Popup (Fixture)",
                        "Onboarding (Fixture)",
                        "Alert (Fixture)",
                        "Backup Confirm (Fixture)",
                        "Death Confirm (Fixture)",
                        "Error (Fixture)",
                        "Waiting (Fixture)",
                        "OOM Page (Fixture)",
                        "Privacy Link (Fixture)",
                        "Code of Conduct (Fixture)",
                        "Restrictions (Fixture)",
                        "Experiments Draft (Fixture)",
                        "Experimental Confirm (Fixture)",
                        "Telemetry Page (Fixture)");
        List<String> cases =
                List.of(
                        "popup",
                        "onboarding",
                        "alert",
                        "backup",
                        "death_confirm",
                        "error",
                        "waiting",
                        "oom",
                        "privacy",
                        "conduct",
                        "restrictions",
                        "experiments",
                        "experimental_confirm",
                        "telemetry");
        List<Supplier<Screen>> factories =
                List.of(
                        () ->
                                new PopupScreen.Builder(this, Component.literal("Popup (Fixture)"))
                                        .addMessage(
                                                Component.literal("Native popup acknowledgement"))
                                        .addButton(CommonComponents.GUI_DONE, PopupScreen::onClose)
                                        .onClose(() -> complete("popup_closed"))
                                        .build(),
                        () ->
                                new AccessibilityOnboardingScreen(
                                        minecraft.options, () -> complete("onboarding_closed")),
                        () ->
                                new AlertScreen(
                                        () -> complete("alert_closed"),
                                        Component.literal("Alert (Fixture)"),
                                        Component.literal(
                                                "Native acknowledgement; no fault was induced")),
                        () ->
                                new BackupConfirmScreen(
                                        () -> complete("backup_cancelled"),
                                        (backup, erase) -> complete("unexpected_backup_proceed"),
                                        Component.literal("Backup (Fixture)"),
                                        Component.literal(
                                                "Cancel-only UI fixture; no world backup is performed"),
                                        true),
                        () ->
                                new DeathScreen.TitleConfirmScreen(
                                        accepted ->
                                                complete(
                                                        accepted
                                                                ? "unexpected_death_confirm"
                                                                : "death_cancelled"),
                                        Component.literal("Death Confirmation (Fixture)"),
                                        Component.literal("No player death was induced"),
                                        CommonComponents.GUI_YES,
                                        CommonComponents.GUI_NO),
                        () ->
                                new ErrorScreen(
                                        Component.literal("Error (Fixture)"),
                                        Component.literal(
                                                "Native error page only; no error was induced")),
                        () ->
                                GenericWaitingScreen.createCompleted(
                                        Component.literal("Waiting (Fixture)"),
                                        Component.literal(
                                                "Native completed state; no background job is running"),
                                        CommonComponents.GUI_DONE,
                                        () -> complete("waiting_closed")),
                        () -> {
                            if (minecraft.level != null || minecraft.getConnection() != null)
                                throw new IllegalStateException(
                                        "The OOM page is allowed only after normal world shutdown");
                            return new OutOfMemoryScreen();
                        },
                        () ->
                                new PrivacyConfirmLinkScreen(
                                        accepted ->
                                                complete(
                                                        accepted
                                                                ? "unexpected_privacy_accept"
                                                                : "privacy_cancelled"),
                                        URI.create("https://example.invalid/privacy-fixture")),
                        // A null server entry prevents the native refusal callback from saving a
                        // server-list entry.
                        () ->
                                new CodeOfConductScreen(
                                        null,
                                        this,
                                        "Native conduct text (Fixture); no server connection or account service is used",
                                        accepted ->
                                                complete(
                                                        accepted
                                                                ? "unexpected_conduct_accept"
                                                                : "conduct_declined")),
                        () -> new RestrictionsScreen(this, ChatAbilities.NO_RESTRICTIONS),
                        this::experiments,
                        this::experimentalConfirmation,
                        () ->
                                new net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen(
                                        this, minecraft.options));
        int top = (height - 192) / 2;
        for (int index = 0; index < labels.size(); index++) {
            String name = cases.get(index);
            Supplier<Screen> factory = factories.get(index);
            Button button =
                    Button.builder(
                                    Component.literal(labels.get(index)),
                                    ignored -> launch(name, factory))
                            .bounds(
                                    width / 2 - 182 + index % 2 * 184,
                                    top + index / 2 * 24,
                                    180,
                                    20)
                            .build();
            if (name.equals("oom"))
                button.active = minecraft.level == null && minecraft.getConnection() == null;
            addRenderableWidget(button);
        }
        addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                        .bounds(width / 2 - 100, top + 168, 200, 20)
                        .build());
        report();
    }

    private void launch(String name, Supplier<Screen> factory) {
        activeCase = name;
        sequence++;
        callbackAction = "none";
        returned = false;
        draftPacks = null;
        selectedPacksBefore = List.of();
        experimentOutputCalls = 0;
        Screen screen = factory.get();
        nativeClass = screen.getClass().getName();
        report();
        minecraft.gui.setScreen(screen);
    }

    private Screen experiments() {
        preparePacks();
        return new ExperimentsScreen(
                this,
                draftPacks,
                selected -> {
                    experimentOutputCalls++;
                    complete("unexpected_experiments_apply");
                });
    }

    private Screen experimentalConfirmation() {
        preparePacks();
        var experimental =
                draftPacks.getAvailablePacks().stream()
                        .filter(pack -> pack.getPackSource() == PackSource.FEATURE)
                        .toList();
        if (experimental.isEmpty())
            throw new IllegalStateException(
                    "Native builtin experimental packs were not discovered");
        return new ConfirmExperimentalFeaturesScreen(
                experimental,
                accepted ->
                        complete(
                                accepted
                                        ? "unexpected_experimental_accept"
                                        : "experimental_cancelled"));
    }

    private void preparePacks() {
        // This repository is private to this fixture; it is not any world or Minecraft's
        // resource-pack repository.
        draftPacks = ServerPacksSource.createVanillaTrustedRepository();
        draftPacks.reload();
        draftPacks.setSelected(List.of("vanilla"));
        selectedPacksBefore = new ArrayList<>(draftPacks.getSelectedIds());
    }

    private void complete(String action) {
        callbackAction = action;
        minecraft.gui.setScreen(this);
    }

    private void report() {
        JsonObject value = new JsonObject();
        value.addProperty("probeOnly", true);
        value.addProperty("arrangeAuthority", "DIAGNOSTIC_native_screen_fixture");
        value.addProperty("fixtureId", fixtureId);
        value.addProperty("processId", ProcessHandle.current().pid());
        value.addProperty("target", System.getProperty("validation.target"));
        value.addProperty("sequence", sequence);
        value.addProperty("case", activeCase);
        value.addProperty("nativeClass", nativeClass);
        value.addProperty("returnedToFixture", returned);
        value.addProperty("callbackAction", callbackAction);
        value.addProperty("actualFaultInduced", false);
        value.addProperty("externalServiceUsed", false);
        value.addProperty("worldFilesTouched", false);
        value.addProperty("globalPackRepositoryTouched", false);
        value.addProperty("serverListEntrySupplied", false);
        value.addProperty("experimentOutputCalls", experimentOutputCalls);
        JsonArray before = new JsonArray(), after = new JsonArray();
        selectedPacksBefore.forEach(before::add);
        if (draftPacks != null) draftPacks.getSelectedIds().forEach(after::add);
        value.add("selectedPacksBefore", before);
        value.add("selectedPacksAfter", after);
        try {
            Path result = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(result);
            Files.writeString(
                    result.resolve(
                            System.getProperty("validation.target")
                                    + "-native-confirmation-probe.json"),
                    value + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Native confirmation fixture facts were not recorded", failure);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(previous);
    }
}
