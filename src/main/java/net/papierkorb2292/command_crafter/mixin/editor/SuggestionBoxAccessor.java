package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.SuggestionBox;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.server.permissions.PermissionSetSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SuggestionBox.class)
public interface SuggestionBoxAccessor {

    @Accessor
    static ClientboundCommandsPacket.NodeInspector<PermissionSetSupplier> getCOMMAND_NODE_INSPECTOR() {
        throw new AssertionError();
    }

    @Invoker
    static <S> void callFillUsableCommands(final CommandNode<S> source, final CommandNode<S> target, final S commandFilter, final Map<CommandNode<S>, CommandNode<S>> converted) {
        throw new AssertionError();
    }
}
