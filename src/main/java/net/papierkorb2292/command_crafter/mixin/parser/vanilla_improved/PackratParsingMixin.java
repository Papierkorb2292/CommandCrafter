package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.packrat.*;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Optional;

@Mixin(PackratParsing.class)
public class PackratParsingMixin {

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/ParsingRules;set(Lnet/minecraft/command/argument/packrat/Symbol;Lnet/minecraft/command/argument/packrat/Term;Lnet/minecraft/command/argument/packrat/ParsingRule$StatelessAction;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=35" // '#' char
                    )
            )
    )
    private static <T, C, P> Term<StringReader> command_crafter$addInlineTagRule(Term<StringReader> original, @Local(argsOnly = true) PackratParsing.Callbacks<T, C, P> callbacks, @Local ParsingRules<StringReader> parsingRules, @Share("inlineTagSymbol") LocalRef<Symbol<T>> inlineTagSymbolRef) {
        if(!(callbacks instanceof InlineTagPackratParsingCallbacks<?> inlineTagRuleProvider))
            return original;

        var inlineTagSymbol = new Symbol<T>("command_crafter:inline_tag");
        inlineTagSymbolRef.set(inlineTagSymbol);
        //noinspection unchecked
        parsingRules.set(inlineTagSymbol, (ParsingRule<StringReader, T>)inlineTagRuleProvider.command_crafter$getInlineTagRule());
        return Term.anyOf(original, Term.symbol(inlineTagSymbol));
    }

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/ParsingRules;set(Lnet/minecraft/command/argument/packrat/Symbol;Lnet/minecraft/command/argument/packrat/Term;Lnet/minecraft/command/argument/packrat/ParsingRule$StatelessAction;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=35" // '#' char
                    )
            )
    )
    private static <T> ParsingRule.StatelessAction<Optional<T>> command_crafter$retrieveInlineTagResult(ParsingRule.StatelessAction<Optional<T>> original, @Share("inlineTagSymbol") LocalRef<Symbol<T>> inlineTagSymbolRef) {
        var inlineTagSymbol = inlineTagSymbolRef.get();
        if(inlineTagSymbol == null)
            return original;
        return result -> {
            var originalResult = original.run(result);
            return originalResult.isPresent() ? originalResult : Optional.ofNullable(result.get(inlineTagSymbol));
        };
    }
}
