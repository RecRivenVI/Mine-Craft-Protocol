package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PlayerCommandConfigurationTest {
    @Test
    void opaqueUiCallbacksFailBeforePublicationAndNeverParseServerPayloads() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.CUSTOM_UI_CALLBACK,
                                        () -> {
                                            fail("Opaque server effects must not be guessed");
                                            return null;
                                        }));
                        assertEquals(
                                "PLAYER_COMMAND_UNCLASSIFIED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.observed.get());
            assertTrue(world.control.snapshot().agentControlled());
        }
    }

    private static final PlayerCommandGate.ConfigurationKind BLOCK =
            PlayerCommandGate.ConfigurationKind.COMMAND_BLOCK;

    @Test
    void nativeHumanOutsideTakeoverIsNotIntercepted() {
        try (var world = new PlayerCommandGateTest.World()) {
            assertTrue(
                    world.gate.allowConfiguration("stop", false, BLOCK, () -> world.parse("stop")));
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void configurationRequiresOriginPermitScopesAndTheExistingTakeover() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            assertFalse(
                    world.gate.allowConfiguration("seed", true, BLOCK, () -> world.parse("seed")));
            world.denied = true;
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "seed", true, BLOCK, () -> world.parse("seed")));
                        assertEquals(
                                "SCOPE_DENIED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            world.denied = false;
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "seed", false, BLOCK, () -> world.parse("seed")));
                        assertEquals(
                                "PLAYER_COMMAND_ORIGIN_UNAVAILABLE",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void evenAConfiguredQueryMutatesConfigurationAndCannotClaimExecutionOrGameplay() {
        for (var kind :
                java.util.List.of(BLOCK, PlayerCommandGate.ConfigurationKind.COMMAND_MINECART)) {
            try (var world = new PlayerCommandGateTest.World()) {
                world.control.acquire();
                world.dispatch(
                        dispatch -> {
                            assertTrue(
                                    world.gate.allowConfiguration(
                                            "seed", true, kind, () -> world.parse("seed")));
                            var receipt = dispatch.receipt();
                            assertEquals(
                                    "command.player.configuration",
                                    receipt.get("type").getAsString());
                            assertEquals(
                                    kind.name(), receipt.get("configurationKind").getAsString());
                            assertEquals(
                                    "INFORMATION",
                                    receipt.get("configuredCommandEffect").getAsString());
                            assertEquals(
                                    "STATE_MUTATION",
                                    receipt.get("effectClassification").getAsString());
                            assertEquals(
                                    "native_player_command_configuration",
                                    receipt.get("classificationSource").getAsString());
                            assertEquals("DEBUG_PRIVILEGED", receipt.get("mode").getAsString());
                            assertEquals(
                                    "submitted_unconfirmed",
                                    receipt.get("submissionStatus").getAsString());
                            assertEquals(
                                    "player_command_configuration",
                                    receipt.get("contaminationReason").getAsString());
                            assertTrue(receipt.get("evidenceContaminated").getAsBoolean());
                            for (String field :
                                    new String[] {
                                        "effectConfirmed",
                                        "serverValidationObserved",
                                        "permissionEscalated",
                                        "gameplayAcceptanceEligible"
                                    }) assertFalse(receipt.get(field).getAsBoolean());
                            assertFalse(receipt.has("command"));
                            receipt.addProperty("command", "private raw text");
                            receipt.addProperty("uuid", "private identity");
                            receipt.add("internal", new com.google.gson.JsonObject());
                            var projected =
                                    io.github.recrivenvi.minecraftprotocol.safety.WorldDataView
                                            .userEvent(receipt);
                            assertEquals("USER", projected.get("dataCategory").getAsString());
                            assertEquals(
                                    kind.name(), projected.get("configurationKind").getAsString());
                            assertEquals(
                                    "INFORMATION",
                                    projected.get("configuredCommandEffect").getAsString());
                            assertTrue(projected.get("evidenceContaminated").getAsBoolean());
                            assertFalse(projected.has("command"));
                            assertFalse(projected.has("uuid"));
                            assertFalse(projected.has("internal"));
                        });
                assertTrue(world.control.snapshot().agentControlled());
                assertEquals(1, world.observed.get());
            }
        }
    }

    @Test
    void jigsawConfigurationIsTypedMutationButGenerationRequiresUnknownServerFacts() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertTrue(
                                world.gate.allowConfiguration(
                                        "minecraft:air",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK,
                                        () -> world.parse("setblock")));
                        var receipt = dispatch.receipt();
                        assertEquals(
                                "BLOCK_STATE",
                                receipt.get("configurationPayloadType").getAsString());
                        assertEquals(
                                "STATE_MUTATION",
                                receipt.get("configuredCommandEffect").getAsString());
                        assertTrue(receipt.get("evidenceContaminated").getAsBoolean());
                        var projection =
                                io.github.recrivenvi.minecraftprotocol.safety.WorldDataView
                                        .userEvent(receipt);
                        assertEquals(
                                "BLOCK_STATE",
                                projection.get("configurationPayloadType").getAsString());
                        assertFalse(projection.has("command"));
                    });
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.JIGSAW_GENERATE,
                                        () -> {
                                            fail(
                                                    "Opaque generation must not guess a command route");
                                            return null;
                                        }));
                        assertEquals(
                                "PLAYER_COMMAND_UNCLASSIFIED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(1, world.observed.get());
            world.denied = true;
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.JIGSAW_GENERATE,
                                        () -> null));
                        assertEquals(
                                "SCOPE_DENIED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(1, world.observed.get());
        }
    }

    @Test
    void jigsawCannotClearOrForgeACommandReceipt() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK,
                                        () -> world.parse("seed")));
                        assertEquals(
                                "INVALID_PLAYER_COMMAND",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PlayerCommandEvidence.configuration(
                                PlayerCommandGate.ConfigurationKind.JIGSAW_BLOCK, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PlayerCommandEvidence.configuration(
                                PlayerCommandGate.ConfigurationKind.JIGSAW_GENERATE,
                                PlayerCommandPolicy.Effect.STATE_MUTATION));
    }

    @Test
    void clearingAConfigurationIsAllowedButControlsAndOversizedInputAreNot() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            for (String blank : new String[] {"", "   "})
                world.dispatch(
                        dispatch -> {
                            assertTrue(
                                    world.gate.allowConfiguration(
                                            blank,
                                            true,
                                            BLOCK,
                                            () -> {
                                                fail(
                                                        "Blank configuration must not parse a command");
                                                return null;
                                            }));
                            assertEquals(
                                    "NONE",
                                    dispatch.receipt()
                                            .get("configuredCommandEffect")
                                            .getAsString());
                        });
            for (String invalid : new String[] {"\n", "\t", " ".repeat(2049)})
                world.dispatch(
                        dispatch -> {
                            assertFalse(
                                    world.gate.allowConfiguration(
                                            invalid, true, BLOCK, () -> world.parse("seed")));
                            assertEquals(
                                    "INVALID_PLAYER_COMMAND",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        });
            assertEquals(0, world.parsed.get());
            assertEquals(2, world.observed.get());
        }
    }

    @Test
    void unsafeAndUnresolvedProgramsFailBeforeAnySubmissionEvidence() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            for (String root : new String[] {"stop", "function"})
                world.dispatch(
                        dispatch -> {
                            assertFalse(
                                    world.gate.allowConfiguration(
                                            root, true, BLOCK, () -> world.parse(root)));
                            assertEquals(
                                    root.equals("stop")
                                            ? "PLAYER_COMMAND_OUT_OF_SCOPE"
                                            : "PLAYER_COMMAND_UNCLASSIFIED",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        });
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void helperCommandAdmissionCannotBeBorrowedToConfigureACommandBlock() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            var pending = new CompletableFuture<com.google.gson.JsonObject>();
            try {
                InputDispatchContext.run(
                        "helper",
                        () -> {},
                        null,
                        "help",
                        true,
                        () -> {
                            try (var dispatch =
                                    world.gate.enter(
                                            world.control.snapshot(),
                                            InputDispatchContext.capture())) {
                                assertFalse(
                                        world.gate.allowConfiguration(
                                                "help", true, BLOCK, () -> world.parse("help")));
                                assertEquals(
                                        "PLAYER_COMMAND_CONTEXT_CHANGED",
                                        assertThrows(
                                                        ProtocolState.ProtocolException.class,
                                                        dispatch::check)
                                                .code());
                            }
                            return pending;
                        });
            } finally {
                pending.cancel(false);
            }
            assertEquals(0, world.parsed.get());
            assertEquals(0, world.observed.get());
        }
    }

    @Test
    void cancelledRevokedAndClosedConfigurationCannotParseOrPublish() {
        for (var kind : PlayerCommandGate.ConfigurationKind.values())
            for (String reason : new String[] {"cancelled", "revoked", "closed"})
                try (var world = new PlayerCommandGateTest.World()) {
                    world.control.acquire();
                    var accepted = world.control.snapshot();
                    var pending = new CompletableFuture<com.google.gson.JsonObject>();
                    var permit =
                            new java.util.concurrent.atomic.AtomicReference<
                                    InputDispatchContext.Permit>();
                    InputDispatchContext.run(
                            "configuration-cancel",
                            () -> {},
                            () -> {
                                permit.set(InputDispatchContext.capture());
                                return pending;
                            });
                    try {
                        switch (reason) {
                            case "cancelled" -> pending.cancel(false);
                            case "revoked" -> world.control.manuallyRevoke();
                            case "closed" -> world.gate.close();
                        }
                        try (var dispatch = world.gate.enter(accepted, permit.get())) {
                            assertFalse(
                                    world.gate.allowConfiguration(
                                            "seed", true, kind, () -> world.parse("seed")));
                            assertThrows(ProtocolState.ProtocolException.class, dispatch::check);
                            assertEquals(0, world.parsed.get());
                            assertEquals(0, world.observed.get());
                        }
                    } finally {
                        pending.cancel(false);
                    }
                }
    }

    @Test
    void structureUpdateAndScanAreNativeParameterMutationsNotInterpretedPrograms() {
        for (var kind :
                java.util.List.of(
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_UPDATE,
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_SCAN)) {
            try (var world = new PlayerCommandGateTest.World()) {
                world.control.acquire();
                world.dispatch(
                        dispatch -> {
                            assertTrue(
                                    world.gate.allowConfiguration(
                                            "",
                                            true,
                                            kind,
                                            () -> {
                                                fail(
                                                        "Native parameter packets must not guess a command parse");
                                                return null;
                                            }));
                            var receipt = dispatch.receipt();
                            assertEquals(
                                    kind.name(), receipt.get("configurationKind").getAsString());
                            assertEquals(
                                    "STRUCTURE_PARAMETERS",
                                    receipt.get("configurationPayloadType").getAsString());
                            assertEquals(
                                    "STATE_MUTATION",
                                    receipt.get("configuredCommandEffect").getAsString());
                            assertTrue(receipt.get("evidenceContaminated").getAsBoolean());
                            assertFalse(receipt.get("effectConfirmed").getAsBoolean());
                            var projected =
                                    io.github.recrivenvi.minecraftprotocol.safety.WorldDataView
                                            .userEvent(receipt);
                            assertEquals(
                                    "STRUCTURE_PARAMETERS",
                                    projected.get("configurationPayloadType").getAsString());
                        });
                assertEquals(1, world.observed.get());
                assertEquals(0, world.parsed.get());
            }
        }
    }

    @Test
    void structureDiskSaveAndOpaqueLoadFailBeforeAnyConfigurationReceipt() {
        for (var kind :
                java.util.List.of(
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_SAVE,
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_LOAD)) {
            try (var world = new PlayerCommandGateTest.World()) {
                world.control.acquire();
                world.dispatch(
                        dispatch -> {
                            assertFalse(
                                    world.gate.allowConfiguration(
                                            "",
                                            true,
                                            kind,
                                            () -> {
                                                fail(
                                                        "Saving/loading must not be disguised as command text");
                                                return null;
                                            }));
                            assertEquals(
                                    kind == PlayerCommandGate.ConfigurationKind.STRUCTURE_SAVE
                                            ? "PLAYER_COMMAND_OUT_OF_SCOPE"
                                            : "PLAYER_COMMAND_UNCLASSIFIED",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        });
                assertEquals(0, world.parsed.get());
                assertEquals(0, world.observed.get());
                world.denied = true;
                world.dispatch(
                        dispatch -> {
                            assertFalse(world.gate.allowConfiguration("", true, kind, () -> null));
                            assertEquals(
                                    "SCOPE_DENIED",
                                    assertThrows(
                                                    ProtocolState.ProtocolException.class,
                                                    dispatch::check)
                                            .code());
                        });
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            PlayerCommandEvidence.configuration(
                                    kind, PlayerCommandPolicy.Effect.STATE_MUTATION));
        }
    }

    @Test
    void structureParametersCannotBorrowHelperPermissionOrForgeDifferentEffects() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "seed",
                                        true,
                                        PlayerCommandGate.ConfigurationKind.STRUCTURE_UPDATE,
                                        () -> null));
                        assertEquals(
                                "INVALID_PLAYER_COMMAND",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(0, world.observed.get());
        }
        for (var kind :
                java.util.List.of(
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_UPDATE,
                        PlayerCommandGate.ConfigurationKind.STRUCTURE_SCAN))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            PlayerCommandEvidence.configuration(
                                    kind, PlayerCommandPolicy.Effect.INFORMATION));
    }

    @Test
    void configurationsShareTheExistingSixteenPacketDispatchBudget() {
        try (var world = new PlayerCommandGateTest.World()) {
            world.control.acquire();
            world.dispatch(
                    dispatch -> {
                        for (int i = 0; i < 16; i++) {
                            assertTrue(
                                    i % 2 == 0
                                            ? world.gate.allow(
                                                    "help", true, true, () -> world.parse("help"))
                                            : world.gate.allowConfiguration(
                                                    "seed",
                                                    true,
                                                    BLOCK,
                                                    () -> world.parse("seed")));
                        }
                        assertFalse(
                                world.gate.allowConfiguration(
                                        "seed", true, BLOCK, () -> world.parse("seed")));
                        assertEquals(
                                "PLAYER_COMMAND_BUDGET_EXCEEDED",
                                assertThrows(ProtocolState.ProtocolException.class, dispatch::check)
                                        .code());
                    });
            assertEquals(16, world.observed.get());
        }
    }
}
