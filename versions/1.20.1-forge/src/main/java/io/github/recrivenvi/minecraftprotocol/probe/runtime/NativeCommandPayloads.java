package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.recrivenvi.minecraftprotocol.core.PlayerCommandPayloads;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.CommandBlockInputAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.CommandItemInputAccessor;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Inspects actual Brigadier argument objects, never SNBT substrings or server/world objects. */
final class NativeCommandPayloads {
    private final PlayerCommandPayloads output = new PlayerCommandPayloads();
    private int nodes;
    private boolean unresolved;

    void capture(CommandContextBuilder<SharedSuggestionProvider> context, String root) {
        if (!Set.of("data", "summon", "setblock", "fill", "give", "item").contains(root)) return;
        var literals =
                context.getNodes().stream()
                        .filter(n -> n.getNode() instanceof LiteralCommandNode<?>)
                        .map(n -> n.getNode().getName())
                        .toList();
        if (root.equals("data") && literals.contains("get")) return;
        if (root.equals("data")
                && literals.contains("modify")
                && (literals.contains("from") || literals.contains("string"))) {
            // The source value is server-owned. Do not guess it from a client cache or read it
            // implicitly.
            unresolved = true;
            return;
        }
        String programPath = "";
        for (var argument :
                context.getArguments().values().stream()
                        .sorted(Comparator.comparingInt(a -> a.getRange().getStart()))
                        .toList()) {
            Object value = argument.getResult();
            if (value instanceof NbtPathArgument.NbtPath path) {
                programPath = PlayerCommandPayloads.pathField(path.toString());
            } else if (value instanceof BlockInput block) {
                output.capture(
                        json(
                                ((CommandBlockInputAccessor) (Object) block)
                                        .minecraftProtocol$commandTag(),
                                0));
            } else if (value instanceof ItemInput item) {
                CompoundTag itemTag =
                        ((CommandItemInputAccessor) (Object) item).minecraftProtocol$commandTag();
                if (itemTag != null) {
                    output.capture(json(itemTag.get("BlockEntityTag"), 0));
                    output.capture(json(itemTag.get("EntityTag"), 0));
                }
            } else if (value instanceof Tag tag) {
                if (tag instanceof StringTag text && programPath.equals("Command"))
                    output.command(text.getAsString());
                else if (tag instanceof StringTag text && programPath.equals("final_state"))
                    output.blockState(text.getAsString());
                else output.capture(json(tag, 0));
            }
        }
    }

    PlayerCommandPayloads output() {
        return output;
    }

    boolean unresolved() {
        return unresolved;
    }

    private JsonElement json(Tag tag, int depth) {
        if (++nodes > 512 || depth > 32)
            throw new ProtocolState.ProtocolException(
                    "PLAYER_COMMAND_BUDGET_EXCEEDED", 413, "Native tag inspection budget exceeded");
        if (tag instanceof CompoundTag compound) {
            var value = new JsonObject();
            for (String key : compound.getAllKeys())
                value.add(key, json(compound.get(key), depth + 1));
            return value;
        }
        if (tag instanceof ListTag list) {
            var value = new JsonArray();
            for (Tag child : list) value.add(json(child, depth + 1));
            return value;
        }
        if (tag instanceof StringTag text) return new JsonPrimitive(text.getAsString());
        return JsonNull.INSTANCE;
    }
}
