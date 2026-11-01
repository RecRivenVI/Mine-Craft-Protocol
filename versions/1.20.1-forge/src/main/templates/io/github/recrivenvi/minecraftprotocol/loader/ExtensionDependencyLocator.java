package io.github.recrivenvi.minecraftprotocol.loader;

import java.nio.file.*;
import java.util.*;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.*;

/** No product classes are linked into the early Loader service layer. */
public final class ExtensionDependencyLocator
        extends net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator {
    @Override
    public String name() {
        return "Mine Craft Protocol extension discovery";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> discovered) {
        List<IModFile> files = new ArrayList<>();
        discovered.forEach(files::add);
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
        if (product == null) return List.of();
        Path jar = product.getFilePath();
        // Development sources are staged by their validation host; production uses only the
        // installed JAR.
        if (!Files.isRegularFile(jar)) return List.of();

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
                                    (FMLLoader.getDist()
                                                    == net.minecraftforge.api.distmarker.Dist.CLIENT
                                            ? "CLIENT"
                                            : "SERVER"),
                                    mods);
            if (library != null) {
                var found = createMod(library);
                if (found.ex() != null) throw found.ex();
                return List.of(found.file());
            }
            return List.of();
        } catch (Exception | LinkageError failed) {
            System.getLogger(getClass().getName())
                    .log(
                            System.Logger.Level.ERROR,
                            "Approved extensionpack discovery failed; no native library was admitted",
                            failed);
            return List.of();
        }
    }
}
