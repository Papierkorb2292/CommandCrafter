package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.CommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandNode.class)
public class CommandNodeMixin {

    @SuppressWarnings("unused")
    @ModifyExpressionValue(
            method = "getRelevantNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C"
            ),
            remap = false
    )
    private char command_crafter$endLiteralWithNewLineOrClosure(char original, StringReader input) {
        if(!VanillaLanguage.Companion.isReaderVanilla(input))
            return original;
        if(VanillaLanguage.Companion.isReaderEasyNextLine(input) && original == '\n')
            return ' ';
        if(((DirectiveStringReader<?>) input).getScopeStack().element().getClosure().endsClosure((DirectiveStringReader<?>) input, true))
            return ' ';
        return original;
    }
}
