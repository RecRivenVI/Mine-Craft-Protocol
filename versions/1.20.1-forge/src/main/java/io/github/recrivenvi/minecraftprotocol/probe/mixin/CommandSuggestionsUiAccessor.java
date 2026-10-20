package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsUiAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList minecraftProtocolProbe$list();

    @Accessor("input")
    EditBox minecraftProtocolProbe$input();
}
