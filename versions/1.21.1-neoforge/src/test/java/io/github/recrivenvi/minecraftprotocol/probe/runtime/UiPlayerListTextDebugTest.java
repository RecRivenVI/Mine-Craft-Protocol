package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class UiPlayerListTextDebugTest {
    @Test
    void productionAdapterCallsTheNativeLocalHeaderAndFooterSetters() throws Exception {
        var overlay = new PlayerTabOverlay(null, null);
        var header = Component.literal("Local header");
        var footer = Component.literal("Local footer");
        UiPresentationDebug.setPlayerListText(overlay, "hud.playerlist.header", header);
        assertSame(header, field(overlay, "header"));
        assertNull(field(overlay, "footer"));
        UiPresentationDebug.setPlayerListText(overlay, "hud.playerlist.footer", footer);
        assertSame(header, field(overlay, "header"));
        assertSame(footer, field(overlay, "footer"));
        assertEquals(
                false, field(overlay, "visible"), "Text replacement does not force Tab visible");
        var empty = Component.literal("");
        UiPresentationDebug.setPlayerListText(overlay, "hud.playerlist.header", empty);
        assertSame(empty, field(overlay, "header"));
        assertSame(footer, field(overlay, "footer"));
    }

    @Test
    void unknownPlayerListFieldsCannotInvokeAnotherNativeSetter() throws Exception {
        var overlay = new PlayerTabOverlay(null, null);
        for (String field :
                List.of("hud.playerlist.row.0", "hud.title", "hud.playerlist.visible")) {
            assertFalse(UiPresentationDebug.playerListField(field));
            assertEquals(
                    "UI_DEBUG_UNSUPPORTED",
                    assertThrows(
                                    ProtocolState.ProtocolException.class,
                                    () ->
                                            UiPresentationDebug.setPlayerListText(
                                                    overlay,
                                                    field,
                                                    Component.literal("not applied")))
                            .code());
        }
        assertNull(field(overlay, "header"));
        assertNull(field(overlay, "footer"));
        assertEquals(false, field(overlay, "visible"));
    }

    @Test
    void productionRoutingAndContentReadersUseTheActualNativeTabListOwner() throws Exception {
        for (String key : List.of("hud.playerlist.header", "hud.playerlist.footer")) {
            assertTrue(UiPresentationDebug.handles(key));
            assertTrue(UiPresentationDebug.playerListField(key));
        }
        for (String key : List.of("hud.title", "hud.subtitle", "hud.actionbar")) {
            assertTrue(UiPresentationDebug.handles(key));
            assertFalse(UiPresentationDebug.playerListField(key));
        }
        var source = compiled();
        int header = 0, footer = 0, ownerCalls = 0, productionDispatch = 0;
        for (var method : source.methods)
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (method.name.equals("setPlayerListText")) {
                    if (call.owner.equals("net/minecraft/client/gui/components/PlayerTabOverlay")
                            && call.name.equals("setHeader")) header++;
                    if (call.owner.equals("net/minecraft/client/gui/components/PlayerTabOverlay")
                            && call.name.equals("setFooter")) footer++;
                    assertNotEquals("setVisible", call.name);
                    assertFalse(call.owner.startsWith("net/minecraft/network/protocol/"));
                }
                if (method.name.equals("describe") && call.name.equals("getTabList")) ownerCalls++;
                if (method.name.equals("mutate") && call.name.equals("setPlayerListText"))
                    productionDispatch++;
            }
        assertEquals(1, header);
        assertEquals(1, footer);
        assertEquals(1, ownerCalls, "Resource keys bind the actual native player-list owner");
        assertEquals(1, productionDispatch);
    }

    private static Object field(PlayerTabOverlay overlay, String name) throws Exception {
        var field = PlayerTabOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(overlay);
    }

    private static ClassNode compiled() throws Exception {
        String name =
                "/io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationDebug.class";
        try (var input = UiPlayerListTextDebugTest.class.getResourceAsStream(name)) {
            assertNotNull(input, name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
