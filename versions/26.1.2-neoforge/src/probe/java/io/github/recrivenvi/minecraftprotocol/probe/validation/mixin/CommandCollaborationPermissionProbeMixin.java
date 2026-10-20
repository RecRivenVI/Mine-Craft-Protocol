package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandCollaborationFixture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class CommandCollaborationPermissionProbeMixin {
    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$commandPermissions(
            NameAndId profile, CallbackInfoReturnable<LevelBasedPermissionSet> ci) {
        LevelBasedPermissionSet permissions =
                CommandCollaborationFixture.permissions((MinecraftServer) (Object) this, profile);
        if (permissions != null) ci.setReturnValue(permissions);
    }
}
