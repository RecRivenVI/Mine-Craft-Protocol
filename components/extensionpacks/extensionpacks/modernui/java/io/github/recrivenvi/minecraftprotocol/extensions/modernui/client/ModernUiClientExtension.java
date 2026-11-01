package io.github.recrivenvi.minecraftprotocol.extensions.modernui.client;

import io.github.recrivenvi.minecraftprotocol.api.extension.*;
import io.github.recrivenvi.minecraftprotocol.extensions.modernui.bridge.ModernBridge;
import java.util.Set;

public final class ModernUiClientExtension implements Extension, UiAdapter, LogicalInputAdapter {
    @Override
    public void initialize(ExtensionContext context) {
        if (context.side() != ExtensionContext.Side.CLIENT) return;
        ModernBridge.presence(context::controlState);
        context.uiAdapter(this);
        context.inputAdapter(this);
        context.processResources()
                .own("framework-input", ModernBridge.cleanupOwner(), ModernBridge::releaseOnUi);
    }

    @Override
    public Descriptor descriptor() {
        return new Descriptor("modernui:visible_views", Set.of(View.USER, View.DEBUG), 512, 250);
    }

    @Override
    public String id() {
        return "modernui:logical_input";
    }

    @Override
    public boolean matches(Frame frame) {
        return frame.screenClass().startsWith("icyllis.modernui.");
    }

    @Override
    public Projection capture(Context context) {
        return ModernBridge.snapshot(
                context.frame(), System.identityHashCode(context.nativeScreen()));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> reset(String reason) {
        return ModernBridge.reset(reason);
    }
}
