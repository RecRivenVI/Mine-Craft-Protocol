package io.github.recrivenvi.minecraftprotocol.core.extension;

import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionResources.Lifetime;
import io.github.recrivenvi.minecraftprotocol.api.extension.LifetimeContexts;
import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Native owner publishes detached identities; readers never inspect game objects off-thread. */
public final class NativeLifetimeContexts {
    private final Map<Lifetime, WeakReference<Object>> observed = new EnumMap<>(Lifetime.class);
    private String layout = "";
    private volatile LifetimeContexts current = LifetimeContexts.empty();

    public synchronized void observe(
            Object world, Object connection, Object screen, String layout) {
        var next = new EnumMap<Lifetime, String>(Lifetime.class);
        update(next, Lifetime.WORLD, world, false);
        update(next, Lifetime.CONNECTION, connection, false);
        update(next, Lifetime.SCREEN, screen, !this.layout.equals(layout));
        this.layout = layout;
        current = new LifetimeContexts(next);
    }

    private void update(Map<Lifetime, String> next, Lifetime kind, Object value, boolean changed) {
        var old = observed.get(kind);
        String id = current.identity(kind);
        if (value != null) {
            if (changed || old == null || old.get() != value || id == null)
                id = UUID.randomUUID().toString();
            next.put(kind, id);
            observed.put(kind, new WeakReference<>(value));
        } else observed.remove(kind);
    }

    public LifetimeContexts snapshot() {
        return current;
    }
}
