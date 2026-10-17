package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.function.Predicate;
import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextFieldHelper.class)
public interface TextFieldUiAccessor {
    @Accessor("stringValidator")
    Predicate<String> minecraftProtocolProbe$validator();
}
