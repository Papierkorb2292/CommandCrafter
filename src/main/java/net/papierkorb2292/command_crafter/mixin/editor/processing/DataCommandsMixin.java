package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.server.commands.data.DataCommands;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(DataCommands.class)
public class DataCommandsMixin {

    @ModifyExpressionValue(
            method = "lambda$register$8", // Targets lambda passed to decorateModification
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/server/commands/data/DataCommands$DataManipulatorDecorator;create(Lnet/minecraft/server/commands/data/DataCommands$DataManipulator;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=insert"
                    )
            )
    )
    private static ArgumentBuilder<CommandSourceStack, ?> command_crafter$setInsertValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> original) {
        command_crafter$addValueDataObjectSource(original, new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.PATH_APPEND_MUTATION, "targetPath"));
        return original;
    }

    @ModifyExpressionValue(
            method = "lambda$register$8", // Targets lambda passed to decorateModification
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/server/commands/data/DataCommands$DataManipulatorDecorator;create(Lnet/minecraft/server/commands/data/DataCommands$DataManipulator;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=prepend"
                    )
            )
    )
    private static ArgumentBuilder<CommandSourceStack, ?> command_crafter$setPrependValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> original) {
        command_crafter$addValueDataObjectSource(original, new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.PATH_APPEND_MUTATION, "targetPath"));
        return original;
    }

    @ModifyExpressionValue(
            method = "lambda$register$8", // Targets lambda passed to decorateModification
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/server/commands/data/DataCommands$DataManipulatorDecorator;create(Lnet/minecraft/server/commands/data/DataCommands$DataManipulator;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=append"
                    )
            )
    )
    private static ArgumentBuilder<CommandSourceStack, ?> command_crafter$setAppendValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> original) {
        command_crafter$addValueDataObjectSource(original, new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.PATH_APPEND_MUTATION, "targetPath"));
        return original;
    }

    @ModifyExpressionValue(
            method = "lambda$register$8", // Targets lambda passed to decorateModification
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/server/commands/data/DataCommands$DataManipulatorDecorator;create(Lnet/minecraft/server/commands/data/DataCommands$DataManipulator;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=set"
                    )
            )
    )
    private static ArgumentBuilder<CommandSourceStack, ?> command_crafter$setSetValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> original) {
        command_crafter$addValueDataObjectSource(original, new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.PATH_SET_MUTATION, "targetPath"));
        return original;
    }

    @ModifyExpressionValue(
            method = "lambda$register$8", // Targets lambda passed to decorateModification
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/server/commands/data/DataCommands$DataManipulatorDecorator;create(Lnet/minecraft/server/commands/data/DataCommands$DataManipulator;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=merge"
                    )
            )
    )
    private static ArgumentBuilder<CommandSourceStack, ?> command_crafter$setMergeValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> original) {
        command_crafter$addValueDataObjectSource(original, new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.PATH_MERGE_MUTATION, "targetPath"));
        return original;
    }

    private static void command_crafter$addValueDataObjectSource(final ArgumentBuilder<CommandSourceStack, ?> valueProviderNode, DataObjectDecoding.DataObjectSource dataObjectSource) {
        if(valueProviderNode instanceof LiteralArgumentBuilder<?> literalBuilder && literalBuilder.getLiteral().equals("value")) {
            for(final var child : literalBuilder.getArguments()) {
                if(child instanceof ArgumentCommandNode<?,?> argument && argument.getName().equals("value") && argument.getType() instanceof NbtTagArgument nbtTagArgument) {
                    ((DataObjectSourceContainer)nbtTagArgument).command_crafter$setDataObjectSource(dataObjectSource);
                }
            }
        }
    }
}
