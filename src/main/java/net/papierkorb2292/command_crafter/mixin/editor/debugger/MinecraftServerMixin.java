package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.MinecraftServer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ServerDebugManagerContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements ServerDebugManagerContainer {

    private final ServerDebugManager command_crafter$serverDebugManager = new ServerDebugManager((MinecraftServer) (Object) this);

    @NotNull
    @Override
public ServerDebugManager command_crafter$getServerDebugManager() {
        return command_crafter$serverDebugManager;
    }

    @ModifyReturnValue(
            method = "reloadResources",
            at = @At("RETURN")
    )
    private CompletableFuture<Void> command_crafter$reloadBreakpoints(CompletableFuture<Void> completionFuture) {
        return completionFuture.thenRun(command_crafter$serverDebugManager::onReload);
    }
}
