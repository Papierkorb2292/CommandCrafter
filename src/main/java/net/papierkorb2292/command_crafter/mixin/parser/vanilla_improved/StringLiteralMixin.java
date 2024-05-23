package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.packrat.Literals;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Literals.StringLiteral.class)
public class StringLiteralMixin {

    @Shadow @Final private String value;

    @ModifyExpressionValue(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"
            )
    )
    private boolean command_crafter$unparseStringLiteral(boolean equals) {
        if(equals) {
            var unparsingList = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
            if (unparsingList != null) {
                unparsingList.add(Either.left(value));
            }
        }
        return equals;
    }
}
