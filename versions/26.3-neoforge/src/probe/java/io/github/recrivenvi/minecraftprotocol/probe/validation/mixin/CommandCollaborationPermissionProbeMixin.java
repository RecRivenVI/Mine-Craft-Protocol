package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandCollaborationFixture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class CommandCollaborationPermissionProbeMixin {
    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$commandPermissions(
            NameAndId profile, CallbackInfoReturnable<LevelBasedPermissionSet> ci) {
        LevelBasedPermissionSet permissions =
                CommandCollaborationFixture.permissions((IntegratedServer) (Object) this, profile);
        if (permissions != null) ci.setReturnValue(permissions);
    }
}
