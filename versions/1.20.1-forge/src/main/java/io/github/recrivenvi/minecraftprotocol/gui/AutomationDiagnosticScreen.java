package io.github.recrivenvi.minecraftprotocol.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AutomationDiagnosticScreen extends Screen {
    private final Screen parent;
    private boolean dynamicControlAdded;

    public AutomationDiagnosticScreen(Screen parent) {
        super(
                Component.translatable(
                        io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID
                                + ".diagnostic.title",
                        io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.DISPLAY_NAME));
        this.parent = parent;
    }

    private static Component label(String key) {
        return Component.translatable(
                io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID
                        + ".diagnostic."
                        + key);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 88;
        EditBox text = new EditBox(this.font, left, top, 200, 20, label("text"));
        text.setValue("phase7");
        this.addRenderableWidget(text);
        this.addRenderableWidget(
                Button.builder(
                                label("action"),
                                button -> button.setMessage(label("action_complete")))
                        .bounds(left, top + 28, 200, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(
                                label("add_dynamic"),
                                button -> {
                                    if (!this.dynamicControlAdded) {
                                        this.dynamicControlAdded = true;
                                        this.addRenderableWidget(
                                                Button.builder(
                                                                label("dynamic"),
                                                                dynamic ->
                                                                        dynamic.setMessage(
                                                                                label(
                                                                                        "dynamic_complete")))
                                                        .bounds(left, top + 140, 200, 20)
                                                        .build());
                                    }
                                    button.setMessage(label("dynamic_added"));
                                })
                        .bounds(left, top + 56, 200, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(
                                label("duplicate"),
                                button -> button.setMessage(label("duplicate_first")))
                        .bounds(left, top + 84, 98, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(
                                label("duplicate"),
                                button -> button.setMessage(label("duplicate_second")))
                        .bounds(left + 102, top + 84, 98, 20)
                        .build());
        Button disabled =
                Button.builder(label("disabled"), button -> {})
                        .bounds(left, top + 112, 98, 20)
                        .build();
        disabled.active = false;
        this.addRenderableWidget(disabled);
        this.addRenderableWidget(
                Button.builder(label("close"), button -> this.onClose())
                        .bounds(left + 102, top + 112, 98, 20)
                        .build());
        // Opt-in diagnostic fixture, not an Agent UI or a public Toast capability.
        if (Boolean.getBoolean("minecraft.protocol.showcaseFixture")) {
            this.addRenderableWidget(
                    Button.builder(
                                    Component.literal("Showcase Toasts (Fixture)"),
                                    button -> this.showcaseToasts())
                            .bounds(left, top + 168, 200, 20)
                            .build());
        }
    }

    private void showcaseToasts() {
        var toasts = this.minecraft.getToasts();
        // Explicit constructor avoids duplicate synthetic parameter names in precompiled mappings.
        class ShowcaseTutorialToast
                extends net.minecraft.client.gui.components.toasts.TutorialToast {
            ShowcaseTutorialToast() {
                super(
                        net.minecraft.client.gui.components.toasts.TutorialToast.Icons.MOUSE,
                        Component.literal("Showcase Fixture"),
                        Component.literal("Tutorial Toast / not gameplay"),
                        false);
            }

            @Override
            public net.minecraft.client.gui.components.toasts.Toast.Visibility render(
                    net.minecraft.client.gui.GuiGraphics graphics,
                    net.minecraft.client.gui.components.toasts.ToastComponent component,
                    long elapsed) {
                if (elapsed >= 12000) this.hide();
                return super.render(graphics, component, elapsed);
            }
        }
        toasts.addToast(new ShowcaseTutorialToast());
        net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(
                toasts,
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastIds
                        .PERIODIC_NOTIFICATION,
                Component.literal("Showcase Fixture"),
                Component.literal("System Toast / not an advancement"));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
