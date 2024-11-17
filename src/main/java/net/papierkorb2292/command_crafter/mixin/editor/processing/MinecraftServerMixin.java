package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Shadow private PlayerManager playerManager;

    @ModifyReturnValue(
            method = "reloadResources",
            at = @At("RETURN")
    )
    private CompletableFuture<Void> command_crafter$reloadBreakpoints(CompletableFuture<Void> completionFuture) {
        return completionFuture.thenRun(() -> {
            for(final var player : playerManager.getPlayerList()) {
                NetworkServerConnectionHandler.INSTANCE.sendDynamicRegistries((MinecraftServer)(Object)this, player.networkHandler);
            }
        });
    }
}
