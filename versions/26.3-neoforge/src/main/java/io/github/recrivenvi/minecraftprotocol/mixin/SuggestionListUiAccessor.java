package io.github.recrivenvi.minecraftprotocol.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionListUiAccessor {
    @Accessor("rect")
    Rect2i mineCraftProtocol$rect();

    @Accessor("suggestionList")
    List<Suggestion> mineCraftProtocol$entries();

    @Accessor("offset")
    int mineCraftProtocol$offset();

    @Accessor("current")
    int mineCraftProtocol$current();

    @Accessor("originalContents")
    String mineCraftProtocol$original();
}
