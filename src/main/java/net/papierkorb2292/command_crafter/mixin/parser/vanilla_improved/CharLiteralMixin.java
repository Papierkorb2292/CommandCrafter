package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.packrat.Literals;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Literals.CharacterLiteral.class)
public class CharLiteralMixin {

    @ModifyExpressionValue(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;read()C",
                    remap = false
            )
    )
    private char command_crafter$unparseCharLiteral(char c) {
        var unparsingListArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        if (unparsingListArg != null) {
            unparsingListArg.getStringified().add(Either.left(String.valueOf(c)));
        }
        return c;
    }
}
