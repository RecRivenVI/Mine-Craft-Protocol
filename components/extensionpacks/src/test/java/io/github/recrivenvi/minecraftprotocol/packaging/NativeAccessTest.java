package io.github.recrivenvi.minecraftprotocol.packaging;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.extensions.create.bridge.NativeAccess;
import org.junit.jupiter.api.Test;

class NativeAccessTest {
    interface DefaultRead {
        default int ready() {
            return 7;
        }
    }

    static class Parent {
        private final int value = 4;

        public String argument(String value) {
            return "text:" + value;
        }
    }

    static final class NativeObject extends Parent implements DefaultRead {
        int calls;

        public String argument(int value) {
            return "number:" + value;
        }

        public void failure() {
            calls++;
            throw new IllegalArgumentException("native_failure");
        }

        public String duplicate(CharSequence value) {
            return "sequence";
        }

        public String duplicate(java.io.Serializable value) {
            return "serializable";
        }
    }

    @Test
    void inheritedAndOverloadedReadsUseActualTypedMembers() {
        var object = new NativeObject();
        assertEquals("number:5", NativeAccess.call(object, "argument", 5));
        assertEquals("text:value", NativeAccess.call(object, "argument", "value"));
        assertEquals(4, NativeAccess.field(object, "value"));
        assertEquals(7, NativeAccess.call(object, "ready"));
        assertThrows(
                IllegalStateException.class,
                () -> NativeAccess.call(object, "duplicate", "ambiguous"));
        assertThrows(IllegalStateException.class, () -> NativeAccess.call(object, "missing"));
    }

    @Test
    void aRealNativeFailureIsNeverRetriedUnderAnotherSignature() {
        var object = new NativeObject();
        assertEquals(
                "native_failure",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> NativeAccess.call(object, "failure"))
                        .getMessage());
        assertEquals(1, object.calls);
    }

    @Test
    void serverBlockQueriesAreTypedAndBoundedBeforeNativeAccess() {
        for (String invalid :
                new String[] {
                    "{}",
                    "{\"x\":0.5,\"y\":80,\"z\":2}",
                    "{\"x\":30000001,\"y\":80,\"z\":2}",
                    "{\"x\":0,\"y\":80,\"z\":2,\"operation\":\"write\"}"
                })
            assertFalse(NativeAccess.blockQuery(JsonParser.parseString(invalid).getAsJsonObject()));
        assertTrue(
                NativeAccess.blockQuery(
                        JsonParser.parseString("{\"x\":0,\"y\":80,\"z\":2}").getAsJsonObject()));
    }
}
