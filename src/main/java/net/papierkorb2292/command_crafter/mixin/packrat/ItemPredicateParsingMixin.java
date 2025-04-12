package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.ItemPredicateParsing;
import net.minecraft.util.packrat.*;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ItemPredicateParsing.class)
public class ItemPredicateParsingMixin {
    private static final Symbol<?> COMMAND_CRAFTER$INLINE_TAG_SYMBOL = new Symbol<>("command_crafter:inline_tag");

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;set(Lnet/minecraft/util/packrat/Symbol;Lnet/minecraft/util/packrat/Term;Lnet/minecraft/util/packrat/ParsingRule$StatelessAction;)Lnet/minecraft/util/packrat/ParsingRuleEntry;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=35" // '#' char
                    )
            )
    )
    private static <T, C, P> Term<StringReader> command_crafter$addInlineTagRule(Term<StringReader> original, @Local(argsOnly = true) ItemPredicateParsing.Callbacks<T, C, P> callbacks, @Local ParsingRules<StringReader> parsingRules) {
        if(!(callbacks instanceof InlineTagPackratParsingCallbacks<?> inlineTagRuleProvider)) {
            return original;
        }
        //noinspection unchecked
        parsingRules.set((Symbol<T>)COMMAND_CRAFTER$INLINE_TAG_SYMBOL, (ParsingRule<StringReader, T>)inlineTagRuleProvider.command_crafter$getInlineTagRule());
        return Term.anyOf(original, parsingRules.term(COMMAND_CRAFTER$INLINE_TAG_SYMBOL));
    }

    @ModifyArg(
            method = "method_58492",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParseResults;getAnyOrThrow([Lnet/minecraft/util/packrat/Symbol;)Ljava/lang/Object;"
            )
    )
    private static <T> Symbol<T>[] command_crafter$retrieveInlineTagResult(Symbol<T>[] original) {
        //noinspection unchecked
        return ArrayUtils.add(original, (Symbol<T>)COMMAND_CRAFTER$INLINE_TAG_SYMBOL);
    }
}
