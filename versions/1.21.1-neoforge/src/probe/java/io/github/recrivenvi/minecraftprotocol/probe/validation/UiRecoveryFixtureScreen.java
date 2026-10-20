package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiRecoveryGraphicsProbeInvoker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DatapackLoadFailureScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.UnsupportedGraphicsWarningScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Native UI Arrange only; no connection, world access, repair worker or real fault is created. */
public final class UiRecoveryFixtureScreen extends Screen {
    private static long nativeTick;
    private final Screen previous;
    private final String fixtureId = UUID.randomUUID().toString();
    private int sequence;
    private String activeCase = "", nativeClass = "", callbackAction = "none";
    private boolean returned, nativeCancelButtonDisabled;
    private Screen nativeScreen;
    private long openedTick = -1, cancelObservedTick = -1, returnedTick = -1;

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiNavigationFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    public UiRecoveryFixtureScreen(Screen previous) {
        super(Component.literal("Recovery UI (Fixture)"));
        if (!enabled())
            throw new IllegalStateException("Recovery UI requires explicit UI validation");
        this.previous = previous;
    }

    @Override
    protected void init() {
        requireDisconnected();
        returned = !activeCase.isEmpty() && minecraft.screen == this;
        List<String> labels =
                List.of(
                        "Disconnected Page (Fixture)",
                        "Datapack Failure (Fixture)",
                        "Graphics Warning (Fixture)");
        List<String> cases = List.of("disconnected", "datapack_failure", "graphics_warning");
        List<Supplier<Screen>> factories =
                List.of(
                        () ->
                                new DisconnectedScreen(
                                        this,
                                        Component.literal("Disconnected (Fixture)"),
                                        Component.literal(
                                                "Native UI only; no network connection was created")),
                        () ->
                                new DatapackLoadFailureScreen(
                                        () -> complete("datapack_cancelled"),
                                        () -> complete("unexpected_datapack_safe_mode")),
                        () ->
                                UiRecoveryGraphicsProbeInvoker.minecraftProtocolProbe$create(
                                        Component.literal("Graphics Warning (Fixture)"),
                                        List.of(
                                                Component.literal(
                                                        "Native warning UI only; no GPU fault or settings change was induced")),
                                        ImmutableList.of(
                                                new UnsupportedGraphicsWarningScreen.ButtonOption(
                                                        CommonComponents.GUI_BACK,
                                                        button -> complete("graphics_back")))));

        int top = height / 2 - 48;
        for (int index = 0; index < labels.size(); index++) {
            String name = cases.get(index);
            Supplier<Screen> factory = factories.get(index);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal(labels.get(index)),
                                    button -> launch(name, factory))
                            .bounds(
                                    width / 2 - 182 + index % 2 * 184,
                                    top + index / 2 * 24,
                                    180,
                                    20)
                            .build());
        }
        addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                        .bounds(width / 2 - 100, top + 72, 200, 20)
                        .build());
        report();
    }

    private void launch(String name, Supplier<Screen> factory) {
        requireDisconnected();
        activeCase = name;
        sequence++;
        callbackAction = "none";
        returned = false;
        nativeCancelButtonDisabled = false;
        openedTick = nativeTick;
        cancelObservedTick = -1;
        returnedTick = -1;
        nativeScreen = factory.get();
        nativeClass = nativeScreen.getClass().getName();
        report();
        minecraft.setScreen(nativeScreen);
    }

    private void complete(String action) {
        callbackAction = action;
        minecraft.setScreen(this);
    }

    private void requireDisconnected() {
        if (minecraft.level != null
                || minecraft.getConnection() != null
                || minecraft.player != null)
            throw new IllegalStateException(
                    "Recovery UI fixtures require normal world shutdown first");
    }

    private void report() {
        JsonObject value = new JsonObject();
        value.addProperty("probeOnly", true);
        value.addProperty("arrangeAuthority", "DIAGNOSTIC_native_screen_fixture");
        value.addProperty("target", System.getProperty("validation.target"));
        value.addProperty("processId", ProcessHandle.current().pid());
        value.addProperty("fixtureId", fixtureId);
        value.addProperty("sequence", sequence);
        value.addProperty("case", activeCase);
        value.addProperty("nativeClass", nativeClass);
        value.addProperty("returnedToFixture", returned);
        value.addProperty("callbackAction", callbackAction);
        value.addProperty("actualFaultInduced", false);
        value.addProperty("networkConnectionCreated", false);
        value.addProperty("externalServiceUsed", false);
        value.addProperty("worldFilesTouched", false);
        value.addProperty("repairOrConversionStarted", false);
        value.addProperty("graphicsSettingsChanged", false);
        JsonObject support = new JsonObject();
        support.addProperty("disconnected", "AVAILABLE");
        support.addProperty("datapack_failure", "AVAILABLE");
        support.addProperty("file_fixer_aborted", "NATIVE_CLASS_NOT_PRESENT");
        support.addProperty("file_fixer_progress", "NATIVE_CLASS_NOT_PRESENT");
        support.addProperty("graphics_warning", "AVAILABLE");
        value.add("caseSupport", support);
        value.addProperty("datapackCancelDestination", "fixture_callback");
        value.addProperty("nativeProgressAvailable", false);
        value.addProperty("nativeProgressCancelled", false);
        value.addProperty("nativeCancelButtonDisabledObserved", nativeCancelButtonDisabled);
        value.addProperty("openedNativeTick", openedTick);
        value.addProperty("cancelObservedNativeTick", cancelObservedTick);
        value.addProperty("returnedNativeTick", returnedTick);
        try {
            Path result = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(result);
            Files.writeString(
                    result.resolve(
                            System.getProperty("validation.target") + "-recovery-ui-probe.json"),
                    value + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Native recovery UI facts were not recorded", failure);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }
}
