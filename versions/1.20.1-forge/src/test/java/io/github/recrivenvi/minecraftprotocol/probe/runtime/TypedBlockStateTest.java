package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;

final class TypedBlockStateTest {
    private enum WireInstrument implements StringRepresentable {
        HARP;

        @Override
        public String getSerializedName() {
            return "harp";
        }
    }

    @Test
    void nativeEnumSerializationIsRoundTrippableEvenWhenToStringIsNotItsWireValue() {
        // The real instrument also references SoundEvents, which requires a running registry.
        // Use its StringRepresentable contract here; the actual note block is covered in Live.
        var property = EnumProperty.create("instrument", WireInstrument.class);
        assertNotEquals("harp", WireInstrument.HARP.toString());
        assertEquals(
                "harp", Phase9ASpikeEngine.serializeDebugProperty(property, WireInstrument.HARP));
        assertEquals(
                WireInstrument.HARP,
                Phase9ASpikeEngine.parseDebugProperty(
                        property,
                        Phase9ASpikeEngine.serializeDebugProperty(property, WireInstrument.HARP)));
    }

    @Test
    void actualNativePropertyDomainsRejectUnknownValuesWithoutCoercion() {
        var age = IntegerProperty.create("age", 0, 7);
        assertEquals(7, Phase9ASpikeEngine.parseDebugProperty(age, "7"));
        assertEquals(
                true, Phase9ASpikeEngine.parseDebugProperty(BooleanProperty.create("lit"), "true"));
        assertEquals(
                SlabType.TOP,
                Phase9ASpikeEngine.parseDebugProperty(
                        EnumProperty.create("type", SlabType.class), "top"));
        for (String value : new String[] {"8", "-1", "true", "1.0"}) {
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> Phase9ASpikeEngine.parseDebugProperty(age, value));
            assertEquals("INVALID_BLOCK_PROPERTY_VALUE", error.code());
        }
    }
}
