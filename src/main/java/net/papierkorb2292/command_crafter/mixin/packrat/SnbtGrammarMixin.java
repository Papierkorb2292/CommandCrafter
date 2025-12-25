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
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.UtilKt;
import net.papierkorb2292.command_crafter.mixin.editor.processing.*;
import net.papierkorb2292.command_crafter.mixin.editor.processing.ByteTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.DoubleTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.EndTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.FloatTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.IntTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.LongTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.ShortTagAccessor;
import net.papierkorb2292.command_crafter.mixin.editor.processing.StringTagAccessor;
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

@Mixin(SnbtGrammar.class)
public class SnbtGrammarMixin {

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;putComplex(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$RuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            )
    )
    private static NamedRule<StringReader, Tag> command_crafter$initStringRangeTreeNode(Dictionary<StringReader> instance, Atom<Tag> symbol, Term<StringReader> term, Rule.RuleAction<StringReader, Tag> action, Operation<NamedRule<StringReader, Tag>> op) {
        return op.call(instance, symbol, (Term<StringReader>) (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().pushNode();
                var cursor = state.mark();
                node.setNodeAllowedStart(cursor);
                state.input().skipWhitespace();
                node.setStartCursor(state.mark());
                state.restore(cursor);
            }
            if(term.parse(state, results, cut))
                return true;
            if(!PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed())
                return false;
            results.put(symbol, command_crafter$createPlaceholder());
            // Skip whitespace so end cursor is set correctly
            state.input().skipWhitespace();
            return true;
        }, action);
    }

    @ModifyReturnValue(
            method = "method_68615",
            at = @At("RETURN")
    )
    private static Object command_crafter$addFinishedNodeToStringRangeTree(Object original, @Local(argsOnly = true) ParseState<?> state, @Local(argsOnly = true) DynamicOps<?> ops) {
        var nbt = (Tag) original;
        var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(builderArg != null) {
            // Primitives cache instances for some values, but a StringRangeTree requires separate instances for
            // all nodes, so the value must be copied to a new instance
            nbt = command_crafter$copyPrimitiveNbtToNewInstance(nbt);
            var currentNode = Objects.requireNonNull(builderArg.getStringRangeTreeBuilder().peekNode());
            currentNode.setEndCursor(state.mark());
            currentNode.setNode(nbt);
            builderArg.getStringRangeTreeBuilder().popNode();
        }
        return nbt;
    }

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;forward(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=list_entries"
                    )
            )
    )
    private static Atom<Tag> command_crafter$allowMalformedListEntry(Atom<Tag> symbol, @Local Dictionary<StringReader> rules) {
        var wrappedSymbol = new Atom<Tag>("command_crafter:allow_malformed_" + symbol.name());
        rules.put(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.named(symbol)),
                        CharSet.of(',', ']'),
                        symbol,
                        SnbtGrammarMixin::command_crafter$createPlaceholder
                ),
                state -> state.get(symbol)
        );
        return wrappedSymbol;
    }

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=59" // ';'
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowMalformedArrayEntryByParsingAsList(Dictionary<StringReader> instance, Atom<List<?>> symbol, Operation<Term<StringReader>> op) {
        var arrayEntriesTerm = op.call(instance, symbol);
        var listEntriesSymbol = UtilKt.getSymbolByName(instance, "list_entries");
        var listEntriesTerm = instance.named(listEntriesSymbol);
        var arrayPrefixSymbol = UtilKt.getSymbolByName(instance, "array_prefix");
        return (state, results, cut) -> {
            if (PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
                var matches = listEntriesTerm.parse(state, results, cut);
                if(matches) {
                    // Clear array prefix so the entries are interpreted as a list
                    results.put(arrayPrefixSymbol, null);
                }
                return matches;
            }
            return arrayEntriesTerm.parse(state, results, cut);
        };
    }

    private static final String command_crafter$malformedCompoundEntryPlaceholderName = "command_crafter:malformed_placeholder";

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;repeatedWithTrailingSeparator(Lnet/minecraft/util/parsing/packrat/NamedRule;Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_entries"
                    )
            )
    )
    private static NamedRule<StringReader, Map.Entry<String, Tag>> command_crafter$allowMalformedCompoundEntry(NamedRule<StringReader, Map.Entry<String, Tag>> rule, @Local Dictionary<StringReader> rules) {
        var symbol = rule.name();
        var wrappedSymbol = new Atom<Map.Entry<String, Tag>>("command_crafter:allow_malformed_" + symbol.name());
        return rules.put(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.named(symbol)),
                        CharSet.of(',', '}'),
                        symbol,
                        // No tag could be parsed, but the rule needs to return something, so a placeholder is added that can be removed when building the compound
                        () -> Map.entry(command_crafter$malformedCompoundEntryPlaceholderName, EndTag.INSTANCE)
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
        return !(key instanceof StringTag(var string) && string.equals(command_crafter$malformedCompoundEntryPlaceholderName) && value instanceof EndTag);
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderTerms;character(C)Lnet/minecraft/util/parsing/packrat/Term;"
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
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderTerms;character(C)Lnet/minecraft/util/parsing/packrat/Term;"
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
                    target = "Lnet/minecraft/util/parsing/packrat/Term;sequence([Lnet/minecraft/util/parsing/packrat/Term;)Lnet/minecraft/util/parsing/packrat/Term;"
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
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;",
                    ordinal = 1
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_key"
                    )
            )
    )
    private static Term<StringReader> command_crafter$allowEmptyMapKey(Dictionary<StringReader> rules, Atom<String> unqoutedKeySymbol, Operation<Term<StringReader>> op) {
        final var unknownKeyCounter = new MutableInt();
        return wrapTermSkipToNextEntryIfMalformed(op.call(rules, unqoutedKeySymbol), CharSet.of(':', '}', ' ', ','), unqoutedKeySymbol, () -> "unknown_" + unknownKeyCounter.getAndIncrement());
    }

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_entry"
                    )
            )
    )
    private static Term<StringReader> command_crafter$saveCompoundKeyRange(Dictionary<StringReader> instance, Atom<String> symbol, Operation<Term<StringReader>> original) {
        final var keyTerm = original.call(instance, symbol);
        return (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().peekNode();
                if(node != null) {
                    var reader = state.input();
                    var initialCursor = reader.getCursor();
                    reader.skipWhitespace();
                    var keyStartCursor = reader.getCursor();
                    reader.setCursor(initialCursor);
                    var matches = keyTerm.parse(state, results, cut);
                    var keyName = matches ? results.getOrThrow(symbol) : command_crafter$malformedCompoundEntryPlaceholderName;
                    node.addMapKeyRange(StringTag.valueOf(keyName), new StringRange(keyStartCursor, state.mark()));
                    return matches;
                }
            }
            return keyTerm.parse(state, results, cut);
        };
    }

    private static EndTag command_crafter$createPlaceholder() {
        var result = EndTagAccessor.callInit();
        var stringRangeTreeBuilderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(stringRangeTreeBuilderArg != null) {
            stringRangeTreeBuilderArg.getStringRangeTreeBuilder().peekNode().setPlaceholder(true);
        }
        return result;
    }

    private static Tag command_crafter$copyPrimitiveNbtToNewInstance(Tag element) {
        if(element instanceof ByteTag nbtByte) {
            return ByteTagAccessor.callInit(nbtByte.byteValue());
        } else if(element instanceof LongTag nbtLong) {
            return LongTagAccessor.callInit(nbtLong.longValue());
        } else if(element instanceof IntTag nbtInt) {
            return IntTagAccessor.callInit(nbtInt.intValue());
        } else if(element instanceof ShortTag nbtShort) {
            return ShortTagAccessor.callInit(nbtShort.shortValue());
        } else if (element instanceof FloatTag nbtFloat) {
            return FloatTagAccessor.callInit(nbtFloat.floatValue());
        } else if(element instanceof DoubleTag nbtDouble) {
            return DoubleTagAccessor.callInit(nbtDouble.doubleValue());
        } else if(element instanceof StringTag nbtString && nbtString.value().isEmpty()) {
            return StringTagAccessor.callInit("");
        }
        return element;
    }

    private static Term<StringReader> command_crafter$wrapTermAllowReaderEndIfMalformed(Term<StringReader> term) {
        return (state, results, cut) -> {
            if(PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
                var reader = state.input();
                if (!reader.canRead()) return true;
            }
            return term.parse(state, results, cut);
        };
    }
}
