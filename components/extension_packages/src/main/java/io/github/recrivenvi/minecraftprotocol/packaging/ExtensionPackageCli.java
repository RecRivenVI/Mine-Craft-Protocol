package io.github.recrivenvi.minecraftprotocol.packaging;

import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionEarlyStage;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionPlatform;
import io.github.recrivenvi.minecraftprotocol.core.extension.ExtensionStore;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

/** Offline host tool; game tokens cannot call it or choose arbitrary filesystem paths. */
public final class ExtensionPackageCli {
    private ExtensionPackageCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Repository, registered Target and client/server role required");
        Path repository = Path.of(args[0]).toRealPath();
        String target = args[1], role = args[2], layout = args[3];
        if (!target.matches("[0-9]+(?:\\.[0-9]+){1,2}-(?:forge|neoforge|fabric)")
                || !java.util.Set.of("client", "server").contains(role)
                || !java.util.Set.of("native", "installed").contains(layout))
            throw new IllegalArgumentException("Registered Target and role required");
        if (!Files.readString(repository.resolve("settings.gradle.kts"))
                .contains("register(\"" + target + "\")"))
            throw new IllegalArgumentException("Target not registered");
        Path game =
                repository
                        .resolve("validations/integration-extension_platform/instance")
                        .resolve(layout.equals("installed") ? "installed" : "")
                        .resolve(target)
                        .resolve(role)
                        .normalize();
        if (!game.startsWith(
                repository.resolve("validations/integration-extension_platform/instance")))
            throw new IllegalArgumentException("Instance escapes the registered validation");
        for (Path at = game; at != null && at.startsWith(repository); at = at.getParent())
            if (Files.isSymbolicLink(at)
                    || Files.exists(at, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isDirectory(at, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Instance ancestors must be real directories");
        if (!Files.isRegularFile(game.resolve("extension-platform-owner.json")))
            throw new IllegalStateException("Owned validation instance required");
        if (Files.exists(game.resolve("minecraft-protocol/endpoint.json")))
            throw new IllegalStateException(
                    "Runtime endpoint present; stop/clean the owned instance first");
        Path lock = game.resolve("minecraft-protocol/instance.lock");
        Files.createDirectories(lock.getParent());
        try (var channel =
                        FileChannel.open(
                                lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var permit = channel.tryLock()) {
            if (permit == null) throw new IllegalStateException("Runtime is active");
            Properties project = new Properties(), facts = new Properties();
            try (var input = Files.newBufferedReader(repository.resolve("gradle.properties"))) {
                project.load(input);
            }
            try (var input =
                    Files.newBufferedReader(
                            repository
                                    .resolve("versions")
                                    .resolve(target)
                                    .resolve("target.properties"))) {
                facts.load(input);
            }
            var env =
                    new ExtensionPlatform.Environment(
                            project.getProperty("mod_id"),
                            target,
                            role.equals("client")
                                    ? PackageContext.Side.CLIENT
                                    : PackageContext.Side.SERVER,
                            Integer.parseInt(facts.getProperty("java_version")),
                            Map.of(),
                            ExtensionPackageCli.class.getClassLoader());
            Path destination =
                    game.resolve("mods").resolve(ExtensionEarlyStage.FILE_NAME + ".pending");
            var result =
                    ExtensionEarlyStage.write(
                            new ExtensionStore(game.resolve("minecraft-protocol/extensions")),
                            env,
                            destination);
            System.out.println(result);
        }
    }
}
