package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.expression.Expression;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.commands.arguments.item.ComponentPredicateParser;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.Rule;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Collections;
import java.util.Optional;

import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermSkipToNextEntryIfMalformed;
import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters;

@Mixin(ComponentPredicateParser.class)
public class ComponentPredicateParserMixin {
    private static final Atom<?> COMMAND_CRAFTER$INLINE_TAG_SYMBOL = new Atom<>("command_crafter:inline_tag");
    private static final Atom<?> COMMAND_CRAFTER$MALFORMED_FALLBACK = new Atom<>("command_crafter:malformed_fallback");

    @Expression("'#'")
    @ModifyArg(
            method = "createGrammar",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;put(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$SimpleRuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static <T, C, P, S extends ComponentPredicateParser.Context<T, C, P>> Term<S> command_crafter$addInlineTagRule(Term<S> original, @Local Dictionary<S> parsingRules) {
        //noinspection unchecked
        parsingRules.put((Atom<T>)COMMAND_CRAFTER$INLINE_TAG_SYMBOL, new VanillaLanguage.InlineTagRule<S, T>());
        return Term.alternative(original, parsingRules.named(COMMAND_CRAFTER$INLINE_TAG_SYMBOL));
    }

    @Expression("'#'")
    @ModifyArg(
            method = "createGrammar",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;put(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$SimpleRuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static <T, C, P, S extends ComponentPredicateParser.Context<T, C, P>> Rule.SimpleRuleAction<S, Optional<T>> command_crafter$retrieveInlineTagRule(Rule.SimpleRuleAction<S, Optional<T>> action) {
        //noinspection unchecked
        return (scope) -> Optional.ofNullable(scope.get((Atom<T>)COMMAND_CRAFTER$INLINE_TAG_SYMBOL)).or(() -> action.run(scope));
    }

    @Expression("'!'")
    @ModifyArg(
            method = "createGrammar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;put(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$SimpleRuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static <T, C, P, S extends ComponentPredicateParser.Context<T, C, P>> Term<S> command_crafter$allowMalformedEntries(Atom<T> symbol, Term<S> term, Rule.SimpleRuleAction<S, T> action) {
        //noinspection unchecked
        return wrapTermSkipToNextEntryIfMalformed(
                term,
                CharSet.of('|', ',', ']'),
                (Atom<T>)COMMAND_CRAFTER$MALFORMED_FALLBACK,
                context -> context.anyOf(Collections.emptyList())
        );
    }

    @Expression("'!'")
    @ModifyArg(
            method = "createGrammar",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;put(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$SimpleRuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static <T, C, P, S extends ComponentPredicateParser.Context<T, C, P>> Rule.SimpleRuleAction<S, T> command_crafter$retrieveMalformedFallback(Rule.SimpleRuleAction<S, T> action) {
        //noinspection unchecked
        return (scope) -> Optional.ofNullable(scope.get((Atom<T>)COMMAND_CRAFTER$MALFORMED_FALLBACK)).orElseGet(() -> action.run(scope));
    }

    private static final ItemPredicateArgument.ComponentWrapper command_crafter$fallbackComponentCheck = new ItemPredicateArgument.ComponentWrapper(Identifier.fromNamespaceAndPath("command_crafter", "fallback"), stack -> true, Codec.PASSTHROUGH.map(dynamic -> stack -> true));

    @WrapOperation(
            method = "createGrammar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            )
    )
    private static <T, C, P, S extends ComponentPredicateParser.Context<T, C, P>> Term<S> command_crafter$allowMalformedIds(
            Dictionary<S> instance,
            Atom<Object> symbol,
            Operation<Term<S>> op,
            @Share("component_type_symbol") LocalRef<Atom<Object>> componentTypeSymbolRef
    ) {
        final var originalTerm = op.call(instance, symbol);
        switch (symbol.name()) {
            case "type" -> {
                return wrapTermSkipToNextEntryIfMalformed(originalTerm, CharSet.of('[', ' '), symbol, _ -> Optional.empty());
            }
            case "component_type" -> {
                componentTypeSymbolRef.set(symbol);
                return wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(
                        originalTerm,
                        CharSet.of(',', '=', '~', '|', ']', ' '),
                        CharSet.of('!'),
                        componentTypeSymbolRef.get(),
                        _ -> command_crafter$fallbackComponentCheck
                );
            }
            case "predicate_type" -> {
                // When malformed, save a value for component_type and not predicate_type, because I already have an instance for that one
                return wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(
                        originalTerm,
                        CharSet.of(',', '=', '~', '|', ']', ' '),
                        CharSet.of('!'),
                        componentTypeSymbolRef.get(),
                        _ -> command_crafter$fallbackComponentCheck
                );
            }
            default -> {
                return originalTerm;
            }
        }
    }
}
