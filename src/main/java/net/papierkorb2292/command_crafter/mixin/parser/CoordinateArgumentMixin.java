package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.argument.CoordinateArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("unused")
@Mixin(CoordinateArgument.class)
public class CoordinateArgumentMixin {

    @ModifyExpressionValue(
            method = {
                    "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/command/argument/CoordinateArgument;",
                    "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/CoordinateArgument;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    ordinal = 1,
                    remap = false
            )
    )
    private static char command_crafter$allowTildeAtEndOfLine(char c) {
        return c == '\n' ? ' ' : c;
    }
}
