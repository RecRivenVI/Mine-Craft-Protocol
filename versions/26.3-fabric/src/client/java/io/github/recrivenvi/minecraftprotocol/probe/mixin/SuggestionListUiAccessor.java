package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionListUiAccessor {
    @Accessor("rect")
    Rect2i minecraftProtocolProbe$rect();

    @Accessor("suggestionList")
    List<Suggestion> minecraftProtocolProbe$entries();

    @Accessor("offset")
    int minecraftProtocolProbe$offset();

    @Accessor("current")
    int minecraftProtocolProbe$current();

    @Accessor("originalContents")
    String minecraftProtocolProbe$original();
}
