package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.SharedSuggestionProvider;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.CustomCompletionsCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin<T> implements AnalyzingCommandNode, CustomCompletionsCommandNode {

    @Shadow(remap = false) @Final private ArgumentType<T> type;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        if(type instanceof AnalyzingCommandNode analyzingCommandNode) {
            analyzingCommandNode.command_crafter$analyze(context, range, reader, result, name);
            return;
        }
        result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull String name) {
        return type instanceof CustomCompletionsCommandNode customCompletionsCommandNode && customCompletionsCommandNode.command_crafter$hasCustomCompletions(context, name);
    }
}
