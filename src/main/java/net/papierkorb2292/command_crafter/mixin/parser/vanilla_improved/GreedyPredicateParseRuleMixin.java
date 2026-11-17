package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.commands.CommandParserState;
import net.minecraft.util.parsing.packrat.commands.GreedyPredicateParseRule;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(GreedyPredicateParseRule.class)
public class GreedyPredicateParseRuleMixin {

    @ModifyVariable(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/String;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;length()I"
            ),
            name = "fullString"
    )
    private String command_crafter$extendStringAtNewline(String fullString, ParseState<CommandParserState> state, @Local(name = "pos") int pos) {
        final var reader = state.input().reader();
        if(reader instanceof DirectiveStringReader<?>) {
            // Extend to cursor, include the next character because the method will make a `<` check
            reader.canRead(pos - reader.getCursor() + 1);
            return reader.getString();
        }
        return fullString;
    }

    @ModifyReturnValue(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/String;",
            at = @At("TAIL")
    )
    private String command_crafter$unparseString(String string) {
        var unparsingListArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        if(unparsingListArg != null) {
            unparsingListArg.getStringified().add(Either.left(string));
        }
        return string;
    }
}
