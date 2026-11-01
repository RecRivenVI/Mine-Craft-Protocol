package io.github.recrivenvi.minecraftprotocol.loader;

import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.*;

/** No product classes are linked into the early Loader service layer. */
public final class ExtensionDependencyLocator implements IDependencyLocator {
    @Override
    public void scanMods(List<IModFile> files, IDiscoveryPipeline pipeline) {
        var mods = new LinkedHashMap<String, String>();
        files.stream()
                .filter(file -> file.getModFileInfo() != null)
                .forEach(
                        file ->
                                file.getModInfos()
                                        .forEach(
                                                mod ->
                                                        mods.put(
                                                                mod.getModId(),
                                                                mod.getVersion().toString())));
        var product =
                files.stream()
                        .filter(file -> file.getModFileInfo() != null)
                        .filter(
                                file ->
                                        file.getModInfos().stream()
                                                .anyMatch(
                                                        mod -> mod.getModId().equals("${mod_id}")))
                        .findFirst()
                        .orElse(null);
        if (product == null) return;
        Path jar = product.getFilePath();
        // Development sources are staged by their validation host; production uses only the
        // installed JAR.
        if (!Files.isRegularFile(jar)) return;

        try (var code =
                new java.net.URLClassLoader(
                        new java.net.URL[] {jar.toUri().toURL()}, getClass().getClassLoader())) {
            var bridge =
                    Class.forName(
                            "io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionDiscoveryLibrary",
                            true,
                            code);
            var method =
                    bridge.getMethod(
                            "prepare",
                            Path.class,
                            String.class,
                            String.class,
                            String.class,
                            Map.class);
            Path library =
                    (Path)
                            method.invoke(
                                    null,
                                    FMLLoader.getGamePath(),
                                    "${mod_id}",
                                    "${target}",
                                    (FMLLoader.getDist() == net.neoforged.api.distmarker.Dist.CLIENT
                                            ? "CLIENT"
                                            : "SERVER"),
                                    mods);
            if (library != null)
                pipeline.addPath(
                        library,
                        ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(this),
                        IncompatibleFileReporting.ERROR);
        } catch (Exception | LinkageError failed) {
            System.getLogger(getClass().getName())
                    .log(
                            System.Logger.Level.ERROR,
                            "Approved extensionpack discovery failed; no native library was admitted",
                            failed);
        }
    }
}
