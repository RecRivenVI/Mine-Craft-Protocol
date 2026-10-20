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
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
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
    private List<String> supportedCases = List.of();

    public UiNativeConfirmationFixtureScreen(Screen previous) {
        super(Component.literal("More Native Screens (Fixture)"));
        if (!enabled())
            throw new IllegalStateException(
                    "Native screen fixtures require explicit UI validation");
        this.previous = previous;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiNavigationFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    @Override
    protected void init() {
        returned = !activeCase.isEmpty() && minecraft.screen == this;
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
                        "Experiments Draft (Fixture)",
                        "Experimental Confirm (Fixture)",
                        "Telemetry Page (Fixture)",
                        "Confirm Link (Fixture)",
                        "Notice Link (Fixture)");
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
                        "experiments",
                        "experimental_confirm",
                        "telemetry",
                        "confirm_link",
                        "notice_link");
        List<Supplier<Screen>> factories =
                List.of(
                        () ->
                                new PopupScreen.Builder(this, Component.literal("Popup (Fixture)"))
                                        .setMessage(
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
                        this::experiments,
                        this::experimentalConfirmation,
                        () ->
                                new net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen(
                                        this, minecraft.options),
                        () ->
                                new ConfirmLinkScreen(
                                        accepted ->
                                                complete(
                                                        accepted
                                                                ? "unexpected_confirm_link_accept"
                                                                : "confirm_link_cancelled"),
                                        "https://example.invalid/confirmation-fixture",
                                        true),
                        () ->
                                new NoticeWithLinkScreen(
                                        Component.literal("Notice Link (Fixture)"),
                                        Component.literal("External navigation is not exercised"),
                                        URI.create("https://example.invalid/notice-fixture"),
                                        () -> complete("notice_link_closed")));
        supportedCases = cases;
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
        minecraft.setScreen(screen);
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
        minecraft.setScreen(this);
    }

    private void report() {
        JsonObject value = new JsonObject();
        value.addProperty("probeOnly", true);
        value.addProperty("arrangeAuthority", "DIAGNOSTIC_native_screen_fixture");
        value.addProperty("fixtureId", fixtureId);
        value.addProperty("processId", ProcessHandle.current().pid());
        value.addProperty("target", System.getProperty("validation.target"));
        JsonArray supported = new JsonArray();
        supportedCases.forEach(supported::add);
        value.add("supportedCases", supported);
        value.addProperty("nativeEntry", "automation_probe_semantic_button");
        value.addProperty("nativeEntryLabel", "More Native Screens (Fixture)");
        JsonObject expectedReturn = new JsonObject();
        boolean returnsToTitle = activeCase.equals("error") || activeCase.equals("oom");
        expectedReturn.addProperty(
                "expectedScreenClassSuffix",
                returnsToTitle ? "TitleScreen" : "UiNativeConfirmationFixtureScreen");
        expectedReturn.addProperty("expectedReturnedToFixture", !returnsToTitle);
        String expectedAction =
                switch (activeCase) {
                    case "popup" -> "popup_closed";
                    case "onboarding" -> "onboarding_closed";
                    case "alert" -> "alert_closed";
                    case "backup" -> "backup_cancelled";
                    case "death_confirm" -> "death_cancelled";
                    case "waiting" -> "waiting_closed";
                    case "experimental_confirm" -> "experimental_cancelled";
                    case "confirm_link" -> "confirm_link_cancelled";
                    case "notice_link" -> "notice_link_closed";
                    default -> "none";
                };
        expectedReturn.addProperty("expectedCallbackAction", expectedAction);
        value.add("nativeReturn", expectedReturn);
        JsonArray unavailable = new JsonArray();
        for (String name : List.of("privacy", "conduct", "restrictions")) {
            JsonObject absent = new JsonObject();
            absent.addProperty("case", name);
            absent.addProperty(
                    "nativeClass",
                    switch (name) {
                        case "privacy" ->
                                "net.minecraft.client.gui.screens.PrivacyConfirmLinkScreen";
                        case "conduct" ->
                                "net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen";
                        case "restrictions" ->
                                "net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen";
                        default -> throw new IllegalStateException(name);
                    });
            absent.addProperty("disposition", "NATIVE_CLASS_ABSENT");
            unavailable.add(absent);
        }
        value.add("nativeUnavailableCases", unavailable);
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
        minecraft.setScreen(previous);
    }
}
