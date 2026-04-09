package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.IsNonPlayerSelector;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(targets = "net/minecraft/server/commands/data/EntityDataAccessor$1")
public class EntityDataAccessDataProviderMixin {

    private final Set<String> command_crafter$mutatingCommands = ImmutableSet.of("merge", "modify", "remove", "result", "success");

    @ModifyExpressionValue(
            method = "wrap",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/EntityArgument;entity()Lnet/minecraft/commands/arguments/EntityArgument;"
            )
    )
    private EntityArgument command_crafter$setIsNonPlayerSelector(EntityArgument entityArgument, ArgumentBuilder<CommandSourceStack, ?> parent) {
        if(parent instanceof LiteralArgumentBuilder<?> literal && command_crafter$mutatingCommands.contains(literal.getLiteral())) {
            ((IsNonPlayerSelector)entityArgument).command_crafter$setIsNonPlayerSelector(true);
        }
        return entityArgument;
    }

    @ModifyExpressionValue(
            method = "wrap",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object command_crafter$setDataObjectSource(Object original, ArgumentBuilder<CommandSourceStack, ?> parent) {
        @SuppressWarnings("unchecked")
        final var builder = (ArgumentBuilder<CommandSourceStack, ?>)original;
        for(final var child : builder.getArguments()) {
            if(!(child instanceof ArgumentCommandNode<?,?> argument))
                continue;
            if(argument.getType() instanceof CompoundTagArgument compoundArg) {
                ((DataObjectSourceContainer)compoundArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.ENTITY_CHANGE, "target"));
            } else if(argument.getType() instanceof NbtPathArgument pathArg) {
                final var kind = parent instanceof LiteralArgumentBuilder<?> literal && command_crafter$mutatingCommands.contains(literal.getLiteral()) ? DataObjectDecoding.DataObjectSourceKind.MUTATING_ENTITY_LOOKUP : DataObjectDecoding.DataObjectSourceKind.ENTITY_LOOKUP;
                ((DataObjectSourceContainer) pathArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(kind, "target"));
            }
        }
        return original;
    }
}
