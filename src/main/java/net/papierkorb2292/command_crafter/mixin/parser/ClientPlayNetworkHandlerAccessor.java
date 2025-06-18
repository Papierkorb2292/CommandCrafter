package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerAccessor {
    @Accessor
    static CommandTreeS2CPacket.NodeFactory<ClientCommandSource> getCOMMAND_NODE_FACTORY() {
        throw new AssertionError();
    }
}
