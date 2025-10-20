package net.papierkorb2292.command_crafter.mixin.packrat;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.nbt.*;
import net.minecraft.util.packrat.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.UtilKt;
import net.papierkorb2292.command_crafter.mixin.editor.processing.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;
import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermAddEntryRanges;
import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.wrapTermSkipToNextEntryIfMalformed;

@Mixin(SnbtParsing.class)
public class SnbtParsingMixin {

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;set(Lnet/minecraft/util/packrat/Symbol;Lnet/minecraft/util/packrat/Term;Lnet/minecraft/util/packrat/ParsingRule$RuleAction;)Lnet/minecraft/util/packrat/ParsingRuleEntry;"
            )
    )
    private static ParsingRuleEntry<StringReader, NbtElement> command_crafter$initStringRangeTreeNode(ParsingRules<StringReader> instance, Symbol<NbtElement> symbol, Term<StringReader> term, ParsingRule.RuleAction<StringReader, NbtElement> action, Operation<ParsingRuleEntry<StringReader, NbtElement>> op) {
        return op.call(instance, symbol, (Term<StringReader>) (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().pushNode();
                var cursor = state.getCursor();
                node.setNodeAllowedStart(cursor);
                state.getReader().skipWhitespace();
                node.setStartCursor(state.getCursor());
                state.setCursor(cursor);
            }
            if(term.matches(state, results, cut))
                return true;
            if(!PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed())
                return false;
            results.put(symbol, command_crafter$createPlaceholder());
            // Skip whitespace so end cursor is set correctly
            state.getReader().skipWhitespace();
            return true;
        }, action);
    }

    @ModifyReturnValue(
            method = "method_68615",
            at = @At("RETURN")
    )
    private static Object command_crafter$addFinishedNodeToStringRangeTree(Object original, @Local(argsOnly = true) ParsingState<?> state, @Local(argsOnly = true) DynamicOps<?> ops) {
        var nbt = (NbtElement) original;
        var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(builderArg != null) {
            // Primitives cache instances for some values, but a StringRangeTree requires separate instances for
            // all nodes, so the value must be copied to a new instance
            nbt = command_crafter$copyPrimitiveNbtToNewInstance(nbt);
            var currentNode = Objects.requireNonNull(builderArg.getStringRangeTreeBuilder().peekNode());
            currentNode.setEndCursor(state.getCursor());
            currentNode.setNode(nbt);
            builderArg.getStringRangeTreeBuilder().popNode();
        }
        return nbt;
    }

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;getOrCreate(Lnet/minecraft/util/packrat/Symbol;)Lnet/minecraft/util/packrat/ParsingRuleEntry;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=list_entries"
                    )
            )
    )
    private static Symbol<NbtElement> command_crafter$allowMalformedListEntry(Symbol<NbtElement> symbol, @Local ParsingRules<StringReader> rules) {
        var wrappedSymbol = new Symbol<NbtElement>("command_crafter:allow_malformed_" + symbol.name());
        rules.set(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.term(symbol)),
                        CharSet.of(',', ']'),
                        symbol,
                        SnbtParsingMixin::command_crafter$createPlaceholder
                ),
                state -> state.get(symbol)
        );
        return wrappedSymbol;
    }

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;term(Lnet/minecraft/util/packrat/Symbol;)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=59" // ';'
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowMalformedArrayEntryByParsingAsList(ParsingRules<StringReader> instance, Symbol<List<?>> symbol, Operation<Term<StringReader>> op) {
        var arrayEntriesTerm = op.call(instance, symbol);
        var listEntriesSymbol = UtilKt.getSymbolByName(instance, "list_entries");
        var listEntriesTerm = instance.term(listEntriesSymbol);
        var arrayPrefixSymbol = UtilKt.getSymbolByName(instance, "array_prefix");
        return (state, results, cut) -> {
            if (PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
                var matches = listEntriesTerm.matches(state, results, cut);
                if(matches) {
                    // Clear array prefix so the entries are interpreted as a list
                    results.put(arrayPrefixSymbol, null);
                }
                return matches;
            }
            return arrayEntriesTerm.matches(state, results, cut);
        };
    }

    private static final String command_crafter$malformedCompoundEntryPlaceholderName = "command_crafter:malformed_placeholder";

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/Term;repeatWithPossiblyTrailingSeparator(Lnet/minecraft/util/packrat/ParsingRuleEntry;Lnet/minecraft/util/packrat/Symbol;Lnet/minecraft/util/packrat/Term;)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_entries"
                    )
            )
    )
    private static ParsingRuleEntry<StringReader, Map.Entry<String, NbtElement>> command_crafter$allowMalformedCompoundEntry(ParsingRuleEntry<StringReader, Map.Entry<String, NbtElement>> rule, @Local ParsingRules<StringReader> rules) {
        var symbol = rule.getSymbol();
        var wrappedSymbol = new Symbol<Map.Entry<String, NbtElement>>("command_crafter:allow_malformed_" + symbol.name());
        return rules.set(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.term(symbol)),
                        CharSet.of(',', '}'),
                        symbol,
                        // No tag could be parsed, but the rule needs to return something, so a placeholder is added that can be removed when building the compound
                        () -> Map.entry(command_crafter$malformedCompoundEntryPlaceholderName, NbtEnd.INSTANCE)
                ),
                results -> results.get(symbol)
        );
    }

    @WrapWithCondition(
            method = "method_68616",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;",
                    remap = false
            )
    )
    private static boolean command_crafter$filterMalformedCompoundPlaceholder(ImmutableMap.Builder<?, ?> instance, Object key, Object value) {
        return !(key instanceof NbtString(var string) && string.equals(command_crafter$malformedCompoundEntryPlaceholderName) && value instanceof NbtEnd);
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/Literals;character(C)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT:LAST",
                            args = "intValue=125" // '}'
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowMissingCompoundEnd(Term<StringReader> term) {
        return command_crafter$wrapTermAllowReaderEndIfMalformed(term);
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/Literals;character(C)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=93" // ']'
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowMissingListEnd(Term<StringReader> term) {
        return command_crafter$wrapTermAllowReaderEndIfMalformed(term);
    }

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/Term;sequence([Lnet/minecraft/util/packrat/Term;)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=quoted_string_literal"
                    ),
                    to = @At(
                            value = "CONSTANT",
                            args = "stringValue=unquoted_string"
                    )
            )
    )
    private static Term<StringReader>[] command_crafter$allowMissingQuotedStringEnd(Term<StringReader>[] terms) {
        terms[terms.length - 1] = command_crafter$wrapTermAllowReaderEndIfMalformed(terms[terms.length - 1]);
        return terms;
    }

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;term(Lnet/minecraft/util/packrat/Symbol;)Lnet/minecraft/util/packrat/Term;",
                    ordinal = 1
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_key"
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowEmptyMapKey(ParsingRules<StringReader> rules, Symbol<String> unqoutedKeySymbol, Operation<Term<StringReader>> op) {
        final var unknownKeyCounter = new MutableInt();
        return wrapTermSkipToNextEntryIfMalformed(op.call(rules, unqoutedKeySymbol), CharSet.of(':', '}', ' ', ','), unqoutedKeySymbol, () -> "unknown_" + unknownKeyCounter.getAndIncrement());
    }

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/packrat/ParsingRules;term(Lnet/minecraft/util/packrat/Symbol;)Lnet/minecraft/util/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_entry"
                    )
            )
    )
    private static Term<StringReader> command_crafter$saveCompoundKeyRange(ParsingRules<StringReader> instance, Symbol<String> symbol, Operation<Term<StringReader>> original) {
        final var keyTerm = original.call(instance, symbol);
        return (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().peekNode();
                if(node != null) {
                    var reader = state.getReader();
                    var initialCursor = reader.getCursor();
                    reader.skipWhitespace();
                    var keyStartCursor = reader.getCursor();
                    reader.setCursor(initialCursor);
                    var matches = keyTerm.matches(state, results, cut);
                    var keyName = matches ? results.getOrThrow(symbol) : command_crafter$malformedCompoundEntryPlaceholderName;
                    node.addMapKeyRange(NbtString.of(keyName), new StringRange(keyStartCursor, state.getCursor()));
                    return matches;
                }
            }
            return keyTerm.matches(state, results, cut);
        };
    }

    private static NbtEnd command_crafter$createPlaceholder() {
        var result = NbtEndAccessor.callInit();
        var stringRangeTreeBuilderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(stringRangeTreeBuilderArg != null) {
            stringRangeTreeBuilderArg.getStringRangeTreeBuilder().peekNode().setPlaceholder(true);
        }
        return result;
    }

    private static NbtElement command_crafter$copyPrimitiveNbtToNewInstance(NbtElement element) {
        if(element instanceof NbtByte nbtByte) {
            return NbtByteAccessor.callInit(nbtByte.byteValue());
        } else if(element instanceof NbtLong nbtLong) {
            return NbtLongAccessor.callInit(nbtLong.longValue());
        } else if(element instanceof NbtInt nbtInt) {
            return NbtIntAccessor.callInit(nbtInt.intValue());
        } else if(element instanceof NbtShort nbtShort) {
            return NbtShortAccessor.callInit(nbtShort.shortValue());
        } else if (element instanceof NbtFloat nbtFloat) {
            return NbtFloatAccessor.callInit(nbtFloat.floatValue());
        } else if(element instanceof NbtDouble nbtDouble) {
            return NbtDoubleAccessor.callInit(nbtDouble.doubleValue());
        } else if(element instanceof NbtString nbtString && nbtString.value().isEmpty()) {
            return NbtStringAccessor.callInit("");
        }
        return element;
    }

    private static Term<StringReader> command_crafter$wrapTermAllowReaderEndIfMalformed(Term<StringReader> term) {
        return (state, results, cut) -> {
            if(PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
                var reader = state.getReader();
                if (!reader.canRead()) return true;
            }
            return term.matches(state, results, cut);
        };
    }
}
