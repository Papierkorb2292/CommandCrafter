package net.papierkorb2292.command_crafter.mixin.editor;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin extends ServerCommonNetworkHandler {
    @Shadow public ServerPlayerEntity player;

    public ServerPlayNetworkHandlerMixin(MinecraftServer server, ClientConnection connection, ConnectedClientData clientData) {
        super(server, connection, clientData);
    }

    @Inject(
            method = "onCustomPayload",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_crafter$callNetworkServerConnectionAsyncHandlers(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        if(NetworkServerConnectionHandler.INSTANCE.callPacketHandler(
                packet.payload(),
                new NetworkServerConnectionHandler.AsyncC2SPacketContext(
                        player,
                        connection
                )
        )) {
            ci.cancel();
        }
    }
}
