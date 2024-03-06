package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtPathArgumentType.class)
public class NbtPathArgumentTypeMixin {

    @ModifyExpressionValue(
            method = "readName",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    ordinal = 0,
                    remap = false
            )
    )
    private static char command_crafter$endTagOnNewline(char c, StringReader reader) {
        return VanillaLanguage.Companion.isReaderEasyNextLine(reader) && c == '\n' ? ' ' : c;
    }

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/NbtPathArgumentType$NbtPath;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    remap = false
            )
    )
    private char command_crafter$endPathOnNewline(char c, StringReader reader) {
        return VanillaLanguage.Companion.isReaderEasyNextLine(reader) && c == '\n' ? ' ' : c;
    }
}
