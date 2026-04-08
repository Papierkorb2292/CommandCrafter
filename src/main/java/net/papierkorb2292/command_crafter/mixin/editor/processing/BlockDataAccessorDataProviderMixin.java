package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/server/commands/data/BlockDataAccessor$1")
public class BlockDataAccessorDataProviderMixin {
    @ModifyExpressionValue(
            method = "wrap",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object command_crafter$setDataObjectSource(Object original) {
        @SuppressWarnings("unchecked")
        final var builder = (ArgumentBuilder<CommandSourceStack, ?>)original;
        for(final var child : builder.getArguments()) {
            if(!(child instanceof ArgumentCommandNode<?,?> argument))
                continue;
            if(argument.getType() instanceof CompoundTagArgument compoundArg) {
                ((DataObjectSourceContainer)compoundArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.BLOCK_ENTITY_CHANGE, "targetPos"));
            } else if(argument.getType() instanceof NbtPathArgument pathArg) {
                ((DataObjectSourceContainer) pathArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.BLOCK_ENTITY_LOOKUP, "targetPos"));
            }
        }
        return original;
    }
}
