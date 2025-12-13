package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.command.argument.ItemPredicateParsing;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.packrat.*;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermSkipToNextEntryIfMalformed;
import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters;

@Mixin(ItemPredicateParsing.class)
public class ItemPredicateParsingMixin {
    private static final Symbol<?> COMMAND_CRAFTER$INLINE_TAG_SYMBOL = new Symbol<>("command_crafter:inline_tag");
    private static final Symbol<?> COMMAND_CRAFTER$MALFORMED_FALLBACK = new Symbol<>("command_crafter:malformed_fallback");

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
            method = "method_58501",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParseResults;getAny([Lnet/minecraft/util/packrat/Symbol;)Ljava/lang/Object;"
            )
    )
    private static <T> Symbol<T>[] command_crafter$retrieveInlineTagResult(Symbol<T>[] original) {
        //noinspection unchecked
        return ArrayUtils.addAll(original, (Symbol<T>)COMMAND_CRAFTER$INLINE_TAG_SYMBOL, (Symbol<T>)COMMAND_CRAFTER$MALFORMED_FALLBACK);
    }

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
                            args = "intValue=33" // '!'
                    )
            )
    )
    private static <T, C, P> Term<StringReader> command_crafter$allowMalformedEntries(Symbol<T> symbol, Term<StringReader> term, ParsingRule.StatelessAction<StringReader, T> action, @Local(argsOnly=true) ItemPredicateParsing.Callbacks<T, C, P> callbacks) {
        //noinspection unchecked
        return wrapTermSkipToNextEntryIfMalformed(
                term,
                CharSet.of('|', ',', ']'),
                (Symbol<T>)COMMAND_CRAFTER$MALFORMED_FALLBACK,
                () -> callbacks.anyOf(Collections.emptyList())
        );
    }

    private static final ItemPredicateArgumentType.ComponentCheck command_crafter$fallbackComponentCheck = new ItemPredicateArgumentType.ComponentCheck(Identifier.of("command_crafter", "fallback"), stack -> true, Codec.PASSTHROUGH.map(dynamic -> stack -> true));

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;term(Lnet/minecraft/util/packrat/Symbol;)Lnet/minecraft/util/packrat/Term;"
            )
    )
    private static Term<StringReader> command_crafter$allowMalformedIds(
            ParsingRules<StringReader> instance,
            Symbol<Object> symbol,
            Operation<Term<StringReader>> op,
            @Share("component_type_symbol") LocalRef<Symbol<Object>> componentTypeSymbolRef
    ) {
        final var originalTerm = op.call(instance, symbol);
        switch (symbol.name()) {
            case "type" -> {
                return wrapTermSkipToNextEntryIfMalformed(originalTerm, CharSet.of('[', ' '), symbol, Optional::empty);
            }
            case "component_type" -> {
                componentTypeSymbolRef.set(symbol);
                return wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(
                        originalTerm,
                        CharSet.of(',', '=', '|', ']', ' '),
                        CharSet.of('!'),
                        componentTypeSymbolRef.get(),
                        () -> command_crafter$fallbackComponentCheck
                );
            }
            case "predicate_type" -> {
                // When malformed, save a value for component_type and not predicate_type, because I already have an instance for that one
                return wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(
                        originalTerm,
                        CharSet.of(',', '~', '|', ']', ' '),
                        CharSet.of('!'),
                        componentTypeSymbolRef.get(),
                        () -> command_crafter$fallbackComponentCheck
                );
            }
            default -> {
                return originalTerm;
            }
        }
    }
}
