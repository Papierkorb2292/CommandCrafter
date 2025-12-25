package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ScoreHolderArgument.class)
public class ScoreHolderArgumentMixin implements AnalyzingCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        if(reader.peek() == '@') {
            var selectorReader = new EntitySelectorParser(reader, true);
            ((AnalyzingResultDataContainer) selectorReader).command_crafter$setAnalyzingResult(result);
            ((AllowMalformedContainer)selectorReader).command_crafter$setAllowMalformed(true);
            selectorReader.parse();
        } else {
            result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @ModifyExpressionValue(
            method = "method_9455",
            at = @At(
                    value = "NEW",
                    target = "(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;"
            )
    )
    private static EntitySelectorParser command_crafter$allowMalformedSuggestions(EntitySelectorParser original) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) != null)
            ((AllowMalformedContainer) original).command_crafter$setAllowMalformed(true);
        return original;
    }
}
