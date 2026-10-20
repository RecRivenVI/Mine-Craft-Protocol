package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import com.mojang.authlib.GameProfile;
import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandCollaborationFixture;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class CommandCollaborationPermissionProbeMixin {
    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$commandPermissions(
            GameProfile profile, CallbackInfoReturnable<Integer> ci) {
        Integer permissions =
                CommandCollaborationFixture.permissions((MinecraftServer) (Object) this, profile);
        if (permissions != null) ci.setReturnValue(permissions);
    }
}
