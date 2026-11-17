package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.tree.CommandNode;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ClientboundCommandsPacket.class)
public interface ClientboundCommandsPacketAccessor {

    @Invoker("<init>")
    static ClientboundCommandsPacket callInit(List<ClientboundCommandsPacket.Entry> entries, int rootIndex) {
        throw new AssertionError();
    }

    @Invoker
    static <S> ClientboundCommandsPacket.Entry callCreateEntry(final CommandNode<? extends S> node, final ClientboundCommandsPacket.NodeInspector<S> inspector, final Object2IntMap<? extends CommandNode<? extends S>> ids) {
        throw new AssertionError();
    }
}
