package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.util.packrat.ParsingState;
import net.minecraft.util.packrat.TokenParsingRule;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TokenParsingRule.class)
public class TokenParsingRuleMixin {

    @ModifyVariable(
            method = "parse(Lnet/minecraft/util/packrat/ParsingState;)Ljava/lang/String;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;length()I"
            )
    )
    private String command_crafter$extendStringAtNewline(String string, ParsingState<StringReader> parsingState, @Local(ordinal = 1) int j) {
        final var reader = parsingState.getReader();
        if(reader instanceof DirectiveStringReader<?>) {
            // Extend to cursor
            reader.canRead(j);
            return reader.getString();
        }
        return string;
    }
}
