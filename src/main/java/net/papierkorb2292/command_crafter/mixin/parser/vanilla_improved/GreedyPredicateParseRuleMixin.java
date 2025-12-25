package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.commands.GreedyPredicateParseRule;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GreedyPredicateParseRule.class)
public class GreedyPredicateParseRuleMixin {

    @ModifyVariable(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/String;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;length()I"
            )
    )
    private String command_crafter$extendStringAtNewline(String string, ParseState<StringReader> parsingState, @Local(ordinal = 1) int j) {
        final var reader = parsingState.input();
        if(reader instanceof DirectiveStringReader<?>) {
            // Extend to cursor
            reader.canRead(j - reader.getCursor());
            return reader.getString();
        }
        return string;
    }
}
