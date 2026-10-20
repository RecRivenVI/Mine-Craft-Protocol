package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class UiReadinessConditionTest {
    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static ConditionEngine conditions(ProtocolState state, JsonObject tree) {
        ProbeService service =
                (ProbeService)
                        Proxy.newProxyInstance(
                                ProbeService.class.getClassLoader(),
                                new Class<?>[] {ProbeService.class},
                                (proxy, method, args) -> {
                                    assertEquals("uiTree", method.getName());
                                    return CompletableFuture.completedFuture(tree.deepCopy());
                                });
        return new ConditionEngine(service, state, null, null, null, null);
    }

    private static JsonObject tree() {
        return json(
                """
                {"screenClass":"","screenIdentity":0,"screenRevision":2,"overlayIdentity":0,
                 "windowWidth":1920,"windowHeight":1080,"width":480,"height":270,"children":[]}
                """);
    }

    private static ProtocolState.ProtocolException failure(
            ConditionEngine engine, JsonObject condition) {
        Throwable error =
                assertThrows(CompletionException.class, () -> engine.assertThat(condition).join());
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return assertInstanceOf(ProtocolState.ProtocolException.class, error);
    }

    @Test
    void uncoveredHudCanBeAwaitedWithoutInventingAnInteractiveSelector() {
        JsonObject tree = tree();
        try (var state = new ProtocolState(Set.of("read", "ui"), "test", ignored -> {});
                var engine = conditions(state, tree)) {
            var condition = json("{\"type\":\"screen\",\"open\":false,\"ready\":true}");
            var result = engine.assertThat(condition).join();
            assertTrue(result.getAsJsonObject("evidence").get("pageReady").getAsBoolean());
            assertEquals(
                    "hud",
                    result.getAsJsonObject("evidence")
                            .getAsJsonObject("readiness")
                            .get("kind")
                            .getAsString());
            assertTrue(engine.waitUntil(condition, 100).join().get("passed").getAsBoolean());
            tree.addProperty("overlayIdentity", 99);
            var denied = failure(engine, condition);
            assertEquals("ASSERTION_FAILED", denied.code());
            assertTrue(
                    denied.details()
                            .getAsJsonObject("readiness")
                            .getAsJsonArray("failures")
                            .toString()
                            .contains("OVERLAY_PRESENT"));
            tree.addProperty("overlayIdentity", 0);
            tree.addProperty("windowWidth", 0);
            assertTrue(
                    failure(engine, condition)
                            .details()
                            .toString()
                            .contains("VIEWPORT_UNAVAILABLE"));
        }
    }

    @Test
    void pageFailureExplainsOnlyTheAuthorizedProjection() {
        JsonObject tree = tree();
        tree.addProperty("screenClass", "TitleScreen");
        tree.addProperty("screenIdentity", 7);
        try (var state = new ProtocolState(Set.of("read", "ui"), "test", ignored -> {});
                var engine = conditions(state, tree)) {
            var condition =
                    json(
                            "{\"type\":\"screen\",\"ready\":true,\"selector\":{\"nodeId\":\"private:hidden\"}}");
            var denied = failure(engine, condition);
            var readiness = denied.details().getAsJsonObject("readiness");
            assertNotNull(readiness, "CF-023: public failure must retain structured readiness");
            assertTrue(
                    readiness
                            .getAsJsonArray("failures")
                            .toString()
                            .contains("TARGET_NOT_FOUND_OR_NOT_VISIBLE"));
            assertFalse(readiness.toString().contains("private:hidden"));
            assertFalse(readiness.has("hiddenMatchCount"));
            tree.add(
                    "children",
                    json("""
                    {"nodes":[{"nodeId":"private:hidden","active":false,"visible":true,"actions":["click"],
                     "x":10,"y":10,"width":20,"height":20}]}
                    """)
                            .getAsJsonArray("nodes"));
            assertTrue(failure(engine, condition).details().toString().contains("TARGET_INACTIVE"));
            tree.getAsJsonArray("children").get(0).getAsJsonObject().addProperty("active", true);
            assertTrue(engine.assertThat(condition).join().get("passed").getAsBoolean());
        }
    }
}
