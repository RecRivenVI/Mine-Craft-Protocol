package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.mojang.brigadier.ParseResults;
import io.github.recrivenvi.minecraftprotocol.probe.validation.ChunkPacketReplay;
import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandConfigurationProbe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Commands.class)
public abstract class CommandsReplayMixin {
    @Inject(method = "performCommand", at = @At("RETURN"))
    private void minecraftProtocolProbe$replay(
            ParseResults<CommandSourceStack> parsed,
            String command,
            CallbackInfoReturnable<Integer> callbackInfo) {
        ChunkPacketReplay.command(parsed.getContext().getSource(), command);
        CommandConfigurationProbe.command(parsed.getContext().getSource(), command);
    }
}
