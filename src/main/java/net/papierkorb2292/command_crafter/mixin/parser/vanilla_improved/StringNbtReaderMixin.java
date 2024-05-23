package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StringNbtReader.class)
public class StringNbtReaderMixin {

    @WrapOperation(
            method = "parseElementPrimitive",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readQuotedString()Ljava/lang/String;",
                    remap = false
            )
    )
    private String command_crafter$allowMultilineString(StringReader reader, Operation<String> op) {
        if(VanillaLanguage.Companion.isReaderEasyNextLine(reader)) {
            return ((DirectiveStringReader<?>)reader).readQuotedMultilineString();
        }
        return op.call(reader);
    }
}
