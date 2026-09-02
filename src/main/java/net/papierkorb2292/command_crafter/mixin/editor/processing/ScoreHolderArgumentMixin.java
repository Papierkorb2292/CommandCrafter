package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ScoreHolderArgument.class)
public class ScoreHolderArgumentMixin {

    @ModifyExpressionValue(
            method = "lambda$static$0",
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
