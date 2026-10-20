package io.github.recrivenvi.minecraftprotocol.probe.validation;

import io.github.recrivenvi.minecraftprotocol.probe.validation.mixin.UiNavigationDialogProbeAccessor;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.dialog.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;

/** Explicit probe Arrange of real native screens, without external navigation or fake callbacks. */
public final class UiNavigationFixtureScreen extends Screen {
    private final Screen previous;

    public UiNavigationFixtureScreen(Screen previous) {
        super(Component.literal("UI Navigation (Fixture)"));
        if (!enabled()) throw new IllegalStateException("UI navigation fixture is not enabled");
        this.previous = previous;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiNavigationFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    @Override
    protected void init() {
        var connection = minecraft.getConnection();
        if (connection == null || minecraft.level == null || minecraft.player == null) {
            addRenderableWidget(
                    Button.builder(Component.translatable("gui.done"), button -> onClose())
                            .bounds(width / 2 - 100, height / 2, 200, 20)
                            .build());
            return;
        }
        DialogConnectionAccess access =
                ((UiNavigationDialogProbeAccessor) connection)
                        .minecraftProtocolProbe$dialogAccess();
        NoticeDialog destination =
                new NoticeDialog(
                        common(
                                "Semantic Link Destination (Fixture)",
                                Component.literal("Native dialog destination")),
                        done());
        Component linked =
                Component.literal("Native text: ")
                        .append(
                                Component.literal("semantic dialog link")
                                        .withStyle(
                                                style ->
                                                        style.withColor(ChatFormatting.AQUA)
                                                                .withUnderlined(true)
                                                                .withClickEvent(
                                                                        new ClickEvent.ShowDialog(
                                                                                Holder.direct(
                                                                                        destination)))))
                        .append("\n")
                        .append(
                                Component.literal("blocked command link")
                                        .withStyle(
                                                style ->
                                                        style.withColor(ChatFormatting.AQUA)
                                                                .withClickEvent(
                                                                        new ClickEvent.RunCommand(
                                                                                "reload"))))
                        .append("\n")
                        .append(
                                Component.literal("blocked custom link")
                                        .withStyle(
                                                style ->
                                                        style.withColor(ChatFormatting.AQUA)
                                                                .withClickEvent(
                                                                        new ClickEvent.Custom(
                                                                                Identifier
                                                                                        .withDefaultNamespace(
                                                                                                "ui_navigation_fixture"),
                                                                                Optional.empty()))))
                        .append(" (Fixture)");
        NoticeDialog notice = new NoticeDialog(common("Notice (Fixture)", linked), done());
        List<String> labels =
                List.of(
                        "Notice Dialog (Fixture)",
                        "Multi Dialog (Fixture)",
                        "Dialog List (Fixture)",
                        "Server Links (Fixture)",
                        "Dialog Warning (Fixture)",
                        "Dialog Waiting (Fixture)",
                        "Confirm Link (Fixture)",
                        "Notice Link (Fixture)");
        List<Supplier<Screen>> screens =
                List.of(
                        () -> DialogScreens.createFromData(notice, this, access),
                        () ->
                                DialogScreens.createFromData(
                                        new MultiActionDialog(
                                                common(
                                                        "Multi (Fixture)",
                                                        Component.literal("Native action buttons")),
                                                List.of(
                                                        new ActionButton(
                                                                new CommonButtonData(
                                                                        Component.literal(
                                                                                "First (Fixture)"),
                                                                        150),
                                                                Optional.empty()),
                                                        new ActionButton(
                                                                new CommonButtonData(
                                                                        Component.literal(
                                                                                "Second (Fixture)"),
                                                                        150),
                                                                Optional.empty())),
                                                Optional.of(done()),
                                                2),
                                        this,
                                        access),
                        () ->
                                DialogScreens.createFromData(
                                        new DialogListDialog(
                                                common(
                                                        "Dialog List (Fixture)",
                                                        Component.literal("Native dialog choices")),
                                                HolderSet.direct(
                                                        Holder.direct(notice),
                                                        Holder.direct(destination)),
                                                Optional.of(done()),
                                                2,
                                                150),
                                        this,
                                        access),
                        () ->
                                DialogScreens.createFromData(
                                        new ServerLinksDialog(
                                                common(
                                                        "Server Links (Fixture)",
                                                        Component.literal(
                                                                "Actual current server links; no service is fabricated")),
                                                Optional.of(done()),
                                                2,
                                                150),
                                        this,
                                        access),
                        () -> DialogScreen.WarningScreen.create(minecraft, access, this),
                        () -> new WaitingForResponseScreen(this),
                        () ->
                                new ConfirmLinkScreen(
                                        accepted -> minecraft.gui.setScreen(this),
                                        URI.create("https://example.invalid/fixture"),
                                        true),
                        () ->
                                new NoticeWithLinkScreen(
                                        Component.literal("Notice Link (Fixture)"),
                                        Component.literal("External navigation is not exercised"),
                                        URI.create("https://example.invalid/fixture"),
                                        () -> minecraft.gui.setScreen(this)));
        int top = (height - 144) / 2;
        for (int index = 0; index < labels.size(); index++) {
            Supplier<Screen> screen = screens.get(index);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal(labels.get(index)),
                                    button -> minecraft.gui.setScreen(screen.get()))
                            .bounds(
                                    width / 2 - 182 + index % 2 * 184,
                                    top + index / 2 * 24,
                                    180,
                                    20)
                            .build());
        }
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), button -> onClose())
                        .bounds(width / 2 - 100, top + 96, 200, 20)
                        .build());
    }

    private static CommonDialogData common(String title, Component body) {
        return new CommonDialogData(
                Component.literal(title),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(body, 280)),
                List.of());
    }

    private static ActionButton done() {
        return new ActionButton(
                new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(previous);
    }
}
