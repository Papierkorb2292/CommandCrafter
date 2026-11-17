package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ItemArgument.class)
public class ItemArgumentMixin {

    @ModifyExpressionValue(
            method = "listSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/item/ItemArgument;getParser(Lnet/minecraft/commands/ParseContext;)Lnet/minecraft/commands/arguments/item/ItemParser;"
            )
    )
    private <S> ItemParser command_crafter$allowMalformedSuggestions(ItemParser original) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) == null)
            // Don't change suggestions for vanilla
            return original;
        // Value doesn't need to be changed back, because this parser is only used once
        ((AllowMalformedContainer) original).command_crafter$setAllowMalformed(true);
        return original;
    }
}
