package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapperContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MessageArgumentType.class)
public abstract class MessageArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow public abstract MessageArgumentType.MessageFormat parse(StringReader stringReader) throws CommandSyntaxException;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var semanticTokens = result.getSemanticTokens();

        var parsedArgument = context.getArgument(name, MessageArgumentType.MessageFormat.class);
        var mapperContainer = (ProcessedInputCursorMapperContainer) parsedArgument;
        if(mapperContainer == null || mapperContainer.command_crafter$getProcessedInputCursorMapper() == null){
            semanticTokens.addAbsoluteMultiline(0, range.getLength(), TokenType.Companion.getENUM_MEMBER(), 0);
            return;
        }

        var mapper = mapperContainer.command_crafter$getProcessedInputCursorMapper();

        try {
            semanticTokens.setCursorMapper(mapper);
            semanticTokens.setCursorOffset(range.getStart());

            semanticTokens.addAbsoluteMultiline(0, range.getLength(), TokenType.Companion.getENUM_MEMBER(), 0);
        } finally {
            semanticTokens.setCursorMapper(null);
        }
    }
}
