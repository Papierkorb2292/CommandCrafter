package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundCommandsPacket.class)
public interface ClientboundCommandsPacketAccessor {

    @Invoker
    static <S> Reference2IntMap<CommandNode<S>> callEnumerateNodes(final RootCommandNode<S> root) {
        throw new AssertionError();
    }
}
