package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.AnalyzedFunctionArgument;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandFunctionArgumentType.class)
public class CommandFunctionArgumentTypeMixin implements SemanticCommandNode {

    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull SemanticTokensBuilder tokens, @NotNull String name) throws CommandSyntaxException {
        var argument = context.getArgument(name, CommandFunctionArgumentType.FunctionArgument.class);
        if (argument instanceof AnalyzedFunctionArgument analyzedArgument) {
            tokens.combineWith(analyzedArgument.getResult().getSemanticTokens());
        }
    }
}
