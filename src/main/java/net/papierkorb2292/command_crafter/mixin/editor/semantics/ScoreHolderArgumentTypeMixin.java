package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.ScoreHolderArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticBuilderContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ScoreHolderArgumentType.class)
public class ScoreHolderArgumentTypeMixin implements AnalyzingCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var selectorReader = new EntitySelectorReader(new StringReader(range.get(context.getInput())));
        ((SemanticBuilderContainer)selectorReader).command_crafter$setSemanticTokensBuilder(result.getSemanticTokens(), range.getStart() + reader.getReadCharacters());
        selectorReader.read();
    }
}
