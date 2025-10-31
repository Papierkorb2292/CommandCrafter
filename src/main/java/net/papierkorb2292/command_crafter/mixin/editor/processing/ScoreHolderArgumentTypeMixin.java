package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.ScoreHolderArgumentType;
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

@Mixin(ScoreHolderArgumentType.class)
public class ScoreHolderArgumentTypeMixin implements AnalyzingCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        if(reader.peek() == '@') {
            var selectorReader = new EntitySelectorReader(reader, true);
            ((AnalyzingResultDataContainer) selectorReader).command_crafter$setAnalyzingResult(result);
            ((AllowMalformedContainer)selectorReader).command_crafter$setAllowMalformed(true);
            selectorReader.read();
        } else {
            result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @ModifyExpressionValue(
            method = "method_9455",
            at = @At(
                    value = "NEW",
                    target = "(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/command/EntitySelectorReader;"
            )
    )
    private static EntitySelectorReader command_crafter$allowMalformedSuggestions(EntitySelectorReader original) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) != null)
            ((AllowMalformedContainer) original).command_crafter$setAllowMalformed(true);
        return original;
    }
}
