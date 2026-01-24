package net.papierkorb2292.command_crafter.mixin.editor;

import kotlin.Unit;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends ServerCommonPacketListenerImpl {
    @Shadow public ServerPlayer player;

    public ServerGamePacketListenerImplMixin(MinecraftServer server, Connection connection, CommonListenerCookie clientData) {
        super(server, connection, clientData);
    }

    @Inject(
            method = "handleCustomPayload",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_crafter$callNetworkServerConnectionAsyncHandlers(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if(NetworkServerConnectionHandler.INSTANCE.callPacketHandler(
                packet.payload(),
                new NetworkServerConnectionHandler.AsyncC2SPacketContext(
                        player,
                        server,
                        connection
                )
        )) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/FutureChain;<init>(Ljava/util/concurrent/Executor;)V"
            )
    )
    private Executor command_crafter$initMergedPacketExecutorForChat(Executor serverExecutor) {
        return runnable -> {
            serverExecutor.execute(command_crafter$alsoRunChatInCustomQueue(runnable));
        };
    }

    @ModifyArg(
            method = "tryHandleChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V"
            )
    )
    private Runnable command_crafter$alsoRunChatInCustomQueue(Runnable original) {
        // Make sure it's only run once
        final var completableFuture = new CompletableFuture<Void>();
        completableFuture.thenRun(original);
        NetworkServerConnectionHandler.INSTANCE.queuePacketHandler(() -> {
            completableFuture.complete(null);
            return Unit.INSTANCE;
        });
        return () -> completableFuture.complete(null);
    }
}
