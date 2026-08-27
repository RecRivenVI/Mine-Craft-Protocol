package io.github.recrivenvi.minecraftprotocol.probe.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AutomationProbeScreen extends Screen {
    private boolean dynamicControlAdded;

    public AutomationProbeScreen() {
        super(Component.literal("Mine-Craft-Protocol Automation Probe"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 88;
        EditBox text = new EditBox(this.font, left, top, 200, 20, Component.literal("Compatibility Text"));
        text.setValue("phase7");
        this.addRenderableWidget(text);
        this.addRenderableWidget(Button.builder(Component.literal("Probe Action"), button ->
                button.setMessage(Component.literal("Probe Action Complete")))
                .bounds(left, top + 28, 200, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Add Dynamic Control"), button -> {
            if (!this.dynamicControlAdded) {
                this.dynamicControlAdded = true;
                this.addRenderableWidget(Button.builder(Component.literal("Dynamic Control"), dynamic ->
                                dynamic.setMessage(Component.literal("Dynamic Control Complete")))
                        .bounds(left, top + 140, 200, 20)
                        .build());
            }
            button.setMessage(Component.literal("Dynamic Control Added"));
        }).bounds(left, top + 56, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Duplicate Action"), button ->
                        button.setMessage(Component.literal("Duplicate Action First Complete")))
                .bounds(left, top + 84, 98, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Duplicate Action"), button ->
                        button.setMessage(Component.literal("Duplicate Action Second Complete")))
                .bounds(left + 102, top + 84, 98, 20)
                .build());
        Button disabled = Button.builder(Component.literal("Disabled Action"), button -> { })
                .bounds(left, top + 112, 98, 20)
                .build();
        disabled.active = false;
        this.addRenderableWidget(disabled);
        this.addRenderableWidget(Button.builder(Component.literal("Close Probe"), button -> this.onClose())
                .bounds(left + 102, top + 112, 98, 20)
                .build());
    }
}
