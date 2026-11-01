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
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
