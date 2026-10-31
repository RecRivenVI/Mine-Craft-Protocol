package io.github.recrivenvi.extensions.modernui;

import io.github.recrivenvi.extensions.modernui.nativebridge.ModernBridge;
import io.github.recrivenvi.minecraftprotocol.probe.extension.*;
import java.util.Set;

public final class Entry implements PackageExtension, UiAdapter, LogicalInputAdapter {
    @Override
    public void initialize(PackageContext context) {
        if (context.side() != PackageContext.Side.CLIENT) return;
        ModernBridge.presence(context::controlState);
        context.uiAdapter(this);
        context.inputAdapter(this);
        context.processResources()
                .own("framework-input", ModernBridge.cleanupOwner(), ModernBridge::releaseOnUi);
    }

    @Override
    public Descriptor descriptor() {
        return new Descriptor("modern_ui:visible_views", Set.of(View.USER, View.DEBUG), 512, 250);
    }

    @Override
    public String id() {
        return "modern_ui:logical_input";
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
