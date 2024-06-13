package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.CustomCompletionsCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.AnalyzedFunctionArgument;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandFunctionArgumentType.class)
public class CommandFunctionArgumentTypeMixin implements AnalyzingCommandNode, CustomCompletionsCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var argument = context.getArgument(name, CommandFunctionArgumentType.FunctionArgument.class);
        if (argument instanceof AnalyzedFunctionArgument analyzedArgument) {
            result.combineWith(analyzedArgument.getResult());
            return;
        }
        var stringArgument = range.get(reader.getString());
        var isTag = stringArgument.startsWith("#");
        var id = Identifier.of(isTag ? stringArgument.substring(1) : stringArgument);
        var fileType = isTag ? PackContentFileType.FUNCTION_TAGS_FILE_TYPE : PackContentFileType.FUNCTIONS_FILE_TYPE;
        IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(id, fileType, range, result, reader);
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<CommandSource> context, @NotNull String name) {
        return context.getArgument(name, CommandFunctionArgumentType.FunctionArgument.class) instanceof AnalyzedFunctionArgument;
    }
}
