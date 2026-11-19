package net.papierkorb2292.command_crafter.mixin.editor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingCommandTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundCommandsPacket$NodeResolver")
public class ClientboundCommandsPacketNodeResolverMixin {

    @ModifyExpressionValue(
            method = "resolve",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/ArgumentBuilder;build()Lcom/mojang/brigadier/tree/CommandNode;"
            )
    )
    private <S> CommandNode<S> command_crafter$resolveAdditionalArgumentData(CommandNode<S> node, int index) {
        if(node instanceof ArgumentCommandNode<S, ?> argument && AnalyzingCommandTree.Companion.getBUILDER().isBound() && AnalyzingCommandTree.Companion.getSERIALIZED_ARGUMENT_DATA().isBound()) {
            final var data = AnalyzingCommandTree.Companion.getSERIALIZED_ARGUMENT_DATA().get().get(index);
            if(data != null)
                AnalyzingCommandTree.Companion.getBUILDER().get().addData(argument.getType(), data);
        }
        return node;
    }
}
