package io.github.recrivenvi.minecraftprotocol.core.extension;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Loader-native subclasses provide metadata only; no client/game singleton may be linked here. */
public abstract class ExtensionMixinGate implements IMixinConfigPlugin {
    private ExtensionEarlyBootstrap bootstrap;
    private String namespace;

    protected abstract Path gameDirectory();

    protected abstract ExtensionPlatform.Environment environment();

    protected void prepareLibrary(Path library) throws Exception {}

    protected final ExtensionEarlyBootstrap initializeBridge() {
        if (bootstrap == null) {
            bootstrap =
                    ExtensionEarlyBootstrap.boot(
                            gameDirectory(),
                            environment(),
                            new ExtensionEarlyBootstrap.Loader() {
                                @Override
                                public void prepare(Path library) throws Exception {
                                    prepareLibrary(library);
                                }

                                @Override
                                public boolean isClassLoaded(String name) {
                                    var tracker = MixinService.getService().getClassTracker();
                                    if (tracker == null)
                                        throw new IllegalStateException(
                                                "Loader has no class-load tracker");
                                    return tracker.isClassLoaded(name);
                                }
                            });
        }
        return bootstrap;
    }

    @Override
    public final void onLoad(String mixinPackage) {
        namespace = mixinPackage;
        initializeBridge();
    }

    @Override
    public final String getRefMapperConfig() {
        return null;
    }

    @Override
    public final boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String sdk =
                io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext.class
                        .getPackageName();
        String coreNamespace = sdk.substring(0, sdk.lastIndexOf('.')) + ".mixin";
        if (coreNamespace.equals(namespace)) return true;
        return bootstrap != null && bootstrap.allows(namespace, mixinClassName, targetClassName);
    }

    @Override
    public final void acceptTargets(Set<String> mine, Set<String> others) {}

    @Override
    public final List<String> getMixins() {
        if (bootstrap != null && bootstrap.extensionNamespace(namespace))
            return bootstrap.mixins(namespace);
        return null;
    }

    @Override
    public final void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo info) {}

    @Override
    public final void postApply(
            String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
        if (bootstrap != null) bootstrap.applied(namespace, mixinClassName, targetClassName);
    }
}
