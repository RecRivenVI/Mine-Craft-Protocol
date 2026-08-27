package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

final class RuntimeToken {
    private RuntimeToken() {
    }

    static String resolve(Path gameDirectory) {
        String configured = System.getProperty("minecraft.protocol.token");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("MCP_RUNTIME_TOKEN");
        }
        String token = configured == null || configured.isBlank() ? generate() : configured;
        Path tokenFile = gameDirectory.resolve("minecraft-protocol").resolve("token");
        try {
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write runtime token file", exception);
        }
        return token;
    }

    private static String generate() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

