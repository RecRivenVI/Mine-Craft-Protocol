package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AttributeMap.class)
public interface AttributeMapAccessor {
    @Accessor("attributes")
    Map<Holder<Attribute>, AttributeInstance> minecraftProtocol$getAttributes();
}
