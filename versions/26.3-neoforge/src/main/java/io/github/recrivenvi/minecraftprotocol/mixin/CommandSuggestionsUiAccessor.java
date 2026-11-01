package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsUiAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList mineCraftProtocol$list();

    @Accessor("input")
    EditBox mineCraftProtocol$input();
}
