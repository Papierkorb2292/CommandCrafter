package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(StringReaderTerms.TerminalWord.class)
public class StringLiteralMixin {

    @Shadow @Final private String value;

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"
            )
    )
    private boolean command_crafter$unparseStringLiteral(boolean equals) {
        if(equals) {
            var unparsingListArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
            if (unparsingListArg != null) {
                unparsingListArg.getStringified().add(Either.left(value));
            }
        }
        return equals;
    }
}
