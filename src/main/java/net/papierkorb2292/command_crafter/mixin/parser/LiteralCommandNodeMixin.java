package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("unused")
@Mixin(LiteralCommandNode.class)
public class LiteralCommandNodeMixin {

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C"
            ),
            remap = false
    )
    private char command_crafter$endLiteralWithNewLineOrClosure(char c, StringReader reader) {
        if(!(reader instanceof DirectiveStringReader<?> directiveStringReader)) {
            return c;
        }
        if(directiveStringReader.getScopeStack().element().getClosure().endsClosure(directiveStringReader, true)) {
            return ' ';
        }
        if(!VanillaLanguage.Companion.isReaderEasyNextLine(reader)) {
            return c;
        }

        if(reader.canRead() && reader.peek() == '\n') {
            return ' ';
        }
        return c;
    }
}
