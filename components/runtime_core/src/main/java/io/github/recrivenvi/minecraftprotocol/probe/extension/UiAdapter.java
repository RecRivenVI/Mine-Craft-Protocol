package io.github.recrivenvi.minecraftprotocol.probe.extension;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/** Owner-thread observation only. Actions remain in the authenticated native input driver. */
public interface UiAdapter {
    enum View {
        USER,
        DEBUG
    }

    enum Space {
        GUI,
        WINDOW,
        FRAMEBUFFER
    }

    record Descriptor(String id, Set<View> views, int maxNodes, int maxAgeMillis) {
        public Descriptor {
            java.util.Objects.requireNonNull(id);
            views = Set.copyOf(views);
            if (views.isEmpty()
                    || maxNodes < 1
                    || maxNodes > 512
                    || maxAgeMillis < 1
                    || maxAgeMillis > 1000)
                throw new IllegalArgumentException("Bounded UI descriptor required");
        }
    }

    /** Detached context; time is deliberately not part of the identity/layout key. */
    record Frame(
            String runtimeId,
            String worldContext,
            String screenClass,
            long screenIdentity,
            long screenRevision,
            int guiWidth,
            int guiHeight,
            int windowWidth,
            int windowHeight,
            int framebufferWidth,
            int framebufferHeight,
            double guiScale) {
        public Frame {
            java.util.Objects.requireNonNull(runtimeId);
            java.util.Objects.requireNonNull(worldContext);
            java.util.Objects.requireNonNull(screenClass);
            if (guiWidth <= 0
                    || guiHeight <= 0
                    || windowWidth <= 0
                    || windowHeight <= 0
                    || framebufferWidth <= 0
                    || framebufferHeight <= 0
                    || !Double.isFinite(guiScale)
                    || guiScale <= 0)
                throw new IllegalArgumentException("Available native UI dimensions required");
        }
    }

    /** Native access is valid only for this synchronous capture on the declared client owner. */
    interface Context {
        Frame frame();

        View view();

        Object nativeScreen();

        com.google.gson.JsonObject nativeTree();

        long identify(Object nativeElement);
    }

    /** A native identity enriches an existing node; zero identifies a separate projected region. */
    record Node(
            String key,
            long nativeElementIdentity,
            String targetRevision,
            Space space,
            JsonObject properties) {
        public Node {
            if (key == null
                    || !key.matches("[A-Za-z0-9_.:-]{1,128}")
                    || nativeElementIdentity < 0
                    || targetRevision == null
                    || targetRevision.length() > 256)
                throw new IllegalArgumentException("Stable UI target identity/revision required");
            java.util.Objects.requireNonNull(space);
            properties = java.util.Objects.requireNonNull(properties).deepCopy();
        }

        @Override
        public JsonObject properties() {
            return properties.deepCopy();
        }
    }

    record Projection(Frame frame, long capturedAtNanos, List<Node> nodes) {
        public Projection {
            java.util.Objects.requireNonNull(frame);
            nodes = List.copyOf(nodes);
        }
    }

    Descriptor descriptor();

    boolean matches(Frame frame);

    /** Return null while an asynchronous framework snapshot is not yet available. Never wait. */
    Projection capture(Context context);
}
