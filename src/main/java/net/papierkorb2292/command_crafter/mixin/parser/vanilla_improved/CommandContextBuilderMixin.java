package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer;
import net.papierkorb2292.command_crafter.parser.helper.DirectiveStringReaderConsumer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandContextBuilder.class)
public class CommandContextBuilderMixin<S> implements DirectiveStringReaderConsumer {

    private DirectiveStringReader<?> command_crafter$reader;

    @Override
    public void command_crafter$setStringReader(@NotNull DirectiveStringReader<?> reader) {
        command_crafter$reader = reader;
    }

    @ModifyExpressionValue(
            method = "withNode",
            at = @At(
                    value = "NEW",
                    target = "com/mojang/brigadier/context/ParsedCommandNode"
            ),
            remap = false
    )
    private ParsedCommandNode<S> command_crafter$addCursorOffsetToParsedNode(ParsedCommandNode<S> parsedNode) {
        if(command_crafter$reader != null)
            ((CursorOffsetContainer) parsedNode).command_crafter$setCursorOffset(command_crafter$reader.getReadCharacters(), command_crafter$reader.getSkippedChars());
        return parsedNode;
    }

    @ModifyExpressionValue(
            method = "copy",
            at = @At(
                    value = "NEW",
                    target = "com/mojang/brigadier/context/CommandContextBuilder"
            ),
            remap = false
    )
    private CommandContextBuilder<S> command_crafter$setCopyReader(CommandContextBuilder<S> builder) {
        if(command_crafter$reader != null)
            ((DirectiveStringReaderConsumer) builder).command_crafter$setStringReader(command_crafter$reader);
        return builder;
    }
}
