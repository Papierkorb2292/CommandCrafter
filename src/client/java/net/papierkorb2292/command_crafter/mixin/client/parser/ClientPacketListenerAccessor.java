package net.papierkorb2292.command_crafter.mixin.client.parser;

import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Accessor
    static ClientboundCommandsPacket.NodeBuilder<ClientSuggestionProvider> getCOMMAND_NODE_BUILDER() {
        throw new AssertionError();
    }
}
