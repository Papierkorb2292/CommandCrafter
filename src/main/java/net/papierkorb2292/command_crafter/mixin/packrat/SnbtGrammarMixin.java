package net.papierkorb2292.command_crafter.mixin.packrat;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.nbt.*;
import net.minecraft.util.parsing.packrat.*;
import net.minecraft.util.parsing.packrat.commands.CommandParserState;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList;
import net.papierkorb2292.command_crafter.editor.processing.TokenInfo;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.LenientUnquotedStringParseRule;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.UnicodeNameSuggestionSupplier;
import net.papierkorb2292.command_crafter.editor.processing.helper.UtilKt;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree;
import net.papierkorb2292.command_crafter.mixin.editor.processing.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;
import static net.papierkorb2292.command_crafter.parser.helper.UtilKt.*;

@Mixin(SnbtGrammar.class)
public class SnbtGrammarMixin {

    @Shadow
    @Final
    private static DelayedException<CommandSyntaxException> ERROR_EXPECTED_UNQUOTED_STRING;

    @Shadow
    @Final
    private static Pattern UNICODE_NAME;

    @Shadow
    @Final
    private static DelayedException<CommandSyntaxException> ERROR_INVALID_CHARACTER_NAME;

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;putComplex(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$RuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            )
    )
    private static NamedRule<CommandParserState, Tag> command_crafter$initStringRangeTreeNode(Dictionary<CommandParserState> instance, Atom<Tag> symbol, Term<CommandParserState> term, Rule.RuleAction<CommandParserState, Tag> action, Operation<NamedRule<CommandParserState, Tag>> op) {
        return op.call(instance, symbol, (Term<CommandParserState>) (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().pushNode();
                var cursor = state.mark();
                node.setNodeAllowedStart(cursor);
                state.input().reader().skipWhitespace();
                node.setStartCursor(state.mark());
                state.restore(cursor);
            }
            if(term.parse(state, results, cut))
                return true;
            if(!PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed())
                return false;
            results.put(symbol, command_crafter$createPlaceholder());
            // Skip whitespace so end cursor is set correctly
            state.input().reader().skipWhitespace();
            if(state.errorCollector() instanceof MalformedParseErrorList malformedParseErrorList) {
                malformedParseErrorList.setLastMalformedEndCursor(state.mark());
            }
            return true;
        }, action);
    }

    @WrapOperation(
            method = "createParser -> *",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/SnbtGrammar$IntegerLiteral;create(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/Object;"
            )
    )
    private static Object command_crafter$allowMalformedInteger(SnbtGrammar.IntegerLiteral instance, DynamicOps<Object> ops, ParseState<?> state, Operation<Object> op) {
        final var original = op.call(instance, ops, state);
        // It's important that a node is not null for the StringRangeTree builder. Additionally, longs need to be allowed without suffix because long arrays are leniently parsed as a list.
        if(original == null && PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
            final var attemptLong = instance.create(ops, SnbtGrammar.TypeSuffix.LONG, state);
            return attemptLong != null ? attemptLong : command_crafter$createPlaceholder();
        }
        return original;
    }

    @ModifyArg(
            method = "createParser",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;putComplex(Lnet/minecraft/util/parsing/packrat/Atom;Lnet/minecraft/util/parsing/packrat/Term;Lnet/minecraft/util/parsing/packrat/Rule$RuleAction;)Lnet/minecraft/util/parsing/packrat/NamedRule;"
            )
    )
    private static <T> Rule.RuleAction<CommandParserState, T> command_crafter$addFinishedNodeToStringRangeTree(Rule.RuleAction<CommandParserState, T> action, @Local(argsOnly = true) DynamicOps<?> ops) {
        return state -> {
            var nbt = (Tag)action.run(state);
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
            //noinspection unchecked
            return (T)nbt;
        };
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
    private static Atom<Tag> command_crafter$allowMalformedListEntry(Atom<Tag> symbol, @Local Dictionary<CommandParserState> rules) {
        var wrappedSymbol = new Atom<Tag>("command_crafter:allow_malformed_" + symbol.name());
        rules.put(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.named(symbol)),
                        CharSet.of(',', ']'),
                        symbol,
                        _ -> SnbtGrammarMixin.command_crafter$createPlaceholder()
                ),
                state -> state.getOrThrow(symbol)
        );
        return wrappedSymbol;
    }

    @Expression("']'")
    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    to = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static Term<CommandParserState> command_crafter$addListTypeHint(Term<CommandParserState> original) {
        return (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if (builderArg != null) {
                final var node = builderArg.getStringRangeTreeBuilder().peekNode();
                if (node != null) {
                    node.setTypeHint(StringRangeTree.NodeTypeHint.LIST);
                }
            }
            return original.parse(state, results, cut);
        };
    }

    @Expression("';'")
    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static Term<CommandParserState> command_crafter$allowMalformedArrayEntryByParsingAsList(Dictionary<CommandParserState> instance, Atom<List<?>> symbol, Operation<Term<CommandParserState>> op) {
        var arrayEntriesTerm = op.call(instance, symbol);
        var listEntriesSymbol = Objects.requireNonNull(UtilKt.getSymbolByName(instance, "list_entries"));
        var listEntriesTerm = instance.named(listEntriesSymbol);
        var arrayPrefixSymbol = Objects.requireNonNull(UtilKt.getSymbolByName(instance, "array_prefix"));
        return (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                final var node = builderArg.getStringRangeTreeBuilder().peekNode();
                if(node != null) {
                    final var arrayType = (Enum<?>) results.getOrThrow(arrayPrefixSymbol);
                    final var typeHint = switch (arrayType.ordinal()) {
                        case 0 -> StringRangeTree.NodeTypeHint.BYTE_ARRAY;
                        case 1 -> StringRangeTree.NodeTypeHint.INT_ARRAY;
                        case 2 -> StringRangeTree.NodeTypeHint.LONG_ARRAY;
                        default -> null;
                    };
                    node.setTypeHint(typeHint);
                }
            }

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
    private static NamedRule<CommandParserState, Map.Entry<String, Tag>> command_crafter$allowMalformedCompoundEntry(NamedRule<CommandParserState, Map.Entry<String, Tag>> rule, @Local Dictionary<CommandParserState> rules) {
        var symbol = rule.name();
        var wrappedSymbol = new Atom<Map.Entry<String, Tag>>("command_crafter:allow_malformed_" + symbol.name());
        return rules.put(
                wrappedSymbol,
                wrapTermSkipToNextEntryIfMalformed(
                        wrapTermAddEntryRanges(rules.named(symbol)),
                        CharSet.of(',', '}'),
                        symbol,
                        // No tag could be parsed, but the rule needs to return something, so a placeholder is added that can be removed when building the compound
                        _ -> Map.entry(command_crafter$malformedCompoundEntryPlaceholderName, EndTag.INSTANCE)
                ),
                results -> results.getOrThrow(symbol)
        );
    }

    @WrapOperation(
            method = "createParser -> *",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"
            )
    )
    private static <T> ImmutableMap.Builder<T, T> command_crafter$filterMalformedCompoundPlaceholder(ImmutableMap.Builder<T, T> instance, T key, T value, Operation<ImmutableMap.Builder<T, T>> original) {
        if (key instanceof StringTag(var string) && string.equals(command_crafter$malformedCompoundEntryPlaceholderName) && value instanceof EndTag)
            return instance;
        return original.call(instance, key, value);
    }

    @Expression("'}'")
    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderTerms;character(C)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION:LAST")
            )
    )
    private static Term<CommandParserState> command_crafter$allowMissingCompoundEnd(Term<CommandParserState> term) {
        return command_crafter$wrapTermAllowReaderEndIfMalformed(term);
    }

    @WrapOperation(
            method = "createParser -> *",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DynamicOps;createMap(Ljava/util/Map;)Ljava/lang/Object;"
            )
    )
    private static Object command_crafter$addCompoundChildren(DynamicOps<?> ops, Map<?, ?> map, Operation<Object> op) {
        final var compound = op.call(ops, map);
        var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(builderArg != null) {
            var node = builderArg.getStringRangeTreeBuilder().peekNode();
            if(node != null) {
                for (final var child : map.values()) {
                    node.addChild((Tag) child);
                }
            }
        }
        return compound;
    }

    @WrapOperation(
            method = "createParser -> *",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DynamicOps;createList(Ljava/util/stream/Stream;)Ljava/lang/Object;"
            )
    )
    private static Object command_crafter$addListChildren(DynamicOps<?> ops, Stream<?> stream, Operation<Object> op) {
        var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if (builderArg == null) return op.call(ops, stream);
        var node = builderArg.getStringRangeTreeBuilder().peekNode();
        if (node == null) return op.call(ops, stream);

        final var entries = new ArrayList<>();
        final var list = op.call(ops, stream.peek(entries::add));
        for (final var child : entries)
            node.addChild((Tag) child);
        return list;
    }

    @Expression("']'")
    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderTerms;character(C)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static Term<CommandParserState> command_crafter$allowMissingListEnd(Term<CommandParserState> term) {
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
    private static Term<CommandParserState>[] command_crafter$allowMissingQuotedStringEnd(Term<CommandParserState>[] terms) {
        terms[terms.length - 1] = command_crafter$wrapTermAllowReaderEndIfMalformed(terms[terms.length - 1]);
        return terms;
    }

    // TODO: Make escape sequences more lenient (also change createCursorMapperForEscapedCharacters)

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
    private static Term<CommandParserState> command_crafter$allowEmptyMapKey(Dictionary<CommandParserState> rules, Atom<String> unqoutedKeySymbol, Operation<Term<CommandParserState>> op) {
        final var unknownKeyCounter = new MutableInt();
        return wrapTermSkipToNextEntryIfMalformed(op.call(rules, unqoutedKeySymbol), CharSet.of(':', '}', ' ', ','), unqoutedKeySymbol, _ -> "unknown_" + unknownKeyCounter.getAndIncrement());
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
    private static Term<CommandParserState> command_crafter$saveCompoundKeyRange(Dictionary<CommandParserState> instance, Atom<String> symbol, Operation<Term<CommandParserState>> original) {
        final var keyTerm = original.call(instance, symbol);
        return (state, results, cut) -> {
            var builderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
            if(builderArg != null) {
                var node = builderArg.getStringRangeTreeBuilder().peekNode();
                if(node != null) {
                    var reader = state.input().reader();
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

    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=unquoted_string_or_builtin"
                    )
            )
    )
    private static Term<CommandParserState> command_crafter$useLenientUnquotedStringRule(Dictionary<CommandParserState> instance, Atom<String> atom, Operation<Term<CommandParserState>> op) {
        final var lenientAtom = Atom.<String>of("command_crafter:lenient_unquoted_string");
        instance.put(lenientAtom, new LenientUnquotedStringParseRule(1, ERROR_EXPECTED_UNQUOTED_STRING));
        return malformedDispatchingTerm(op.call(instance, atom), instance.namedWithAlias(lenientAtom, atom));
    }

    private static EndTag command_crafter$createPlaceholder() {
        var result = EndTagAccessor.callInit();
        var stringRangeTreeBuilderArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder());
        if(stringRangeTreeBuilderArg != null) {
            stringRangeTreeBuilderArg.getStringRangeTreeBuilder().peekNode().setPlaceholder(true);
        }
        return result;
    }

    @Expression("'N'")
    @WrapOperation(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION")
            )
    )
    private static Term<CommandParserState> command_crafter$suggestUnicodeNames(Dictionary<CommandParserState> instance, Atom<String> atom, Operation<Term<CommandParserState>> op) {
        // Add the error directly after the term was parsed instead of where Minecraft usually validates the character name,
        // because otherwise the error from the missing '}' would suppress the character name error
        final var original = op.call(instance, atom);
        return (parseState, scope, control) -> {
            final var startCursor = parseState.mark();
            final var matches = original.parse(parseState, scope, control);
            final var name = parseState.scope().get(atom);
            if(name != null) {
                try {
                    Character.codePointOf(name);
                    return matches;
                } catch (IllegalArgumentException ignored) { }
            }
            parseState.errorCollector().store(startCursor, UnicodeNameSuggestionSupplier.INSTANCE, ERROR_INVALID_CHARACTER_NAME);
            return false;
        };
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

    private static Term<CommandParserState> command_crafter$wrapTermAllowReaderEndIfMalformed(Term<CommandParserState> term) {
        return (state, results, cut) -> {
            if(PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed()) {
                var reader = state.input().reader();
                if (!reader.canRead()) {
                    if(state.errorCollector() instanceof MalformedParseErrorList malformedParseErrorList) {
                        malformedParseErrorList.setLastMalformedEndCursor(state.mark());
                    }
                    return true;
                }
            }
            return term.parse(state, results, cut);
        };
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
                            args = "stringValue=unquoted_string_or_builtin"
                    )
            )
    )
    private static Term<CommandParserState> command_crafter$highlightUnquotedStringOrBuiltin(Dictionary<CommandParserState> rules, Atom<String> unquotedString, Operation<Term<CommandParserState>> op) {
        final var builtinLookahead = Term.positiveLookahead(StringReaderTerms.character('('));
        return wrapTermWithSemanticToken(op.call(rules, unquotedString), (state, scope, range) -> {
            final var mark = state.mark();
            state.restore(range.getEnd());
            final var isBuiltin = builtinLookahead.parse(state, new Scope(), Control.UNBOUND);
            state.restore(mark);
            if(isBuiltin)
                return new TokenInfo(TokenType.Companion.getFUNCTION(), 0);
            final var string = scope.getOrThrow(unquotedString);
            if(string.equalsIgnoreCase("true") || string.equalsIgnoreCase("false"))
                return new TokenInfo(TokenType.Companion.getENUM_MEMBER(), 0);
            return new TokenInfo(TokenType.Companion.getSTRING(), 0);
        });
    }

    @Expression("'\"'")
    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Dictionary;named(Lnet/minecraft/util/parsing/packrat/Atom;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At("MIXINEXTRAS:EXPRESSION:LAST")
            )
    )
    private static Term<CommandParserState> command_crafter$highlightQuotedString(Term<CommandParserState> original) {
        return wrapTermWithSemanticToken(original, TokenType.Companion.getSTRING());
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;alternative([Lnet/minecraft/util/parsing/packrat/Term;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/nbt/SnbtGrammar;NUMBER_LOOKEAHEAD:Lnet/minecraft/util/parsing/packrat/commands/StringReaderTerms$TerminalCharacters;",
                            opcode = Opcodes.GETSTATIC
                    )
            )
    )
    private static Term<CommandParserState> command_crafter$highlightNumber(Term<CommandParserState> original) {
        return wrapTermWithSemanticToken(original, TokenType.Companion.getNUMBER());
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;alternative([Lnet/minecraft/util/parsing/packrat/Term;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=map_key"
                    )
            )
    )
    private static Term<CommandParserState> command_crafter$highlightMapKey(Term<CommandParserState> original) {
        return wrapTermWithSemanticToken(original, TokenType.Companion.getPROPERTY());
    }

    @ModifyExpressionValue(
            method = "createParser",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;alternative([Lnet/minecraft/util/parsing/packrat/Term;)Lnet/minecraft/util/parsing/packrat/Term;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=array_prefix"
                    )
            )
    )
    private static Term<CommandParserState> command_crafter$highlightArrayPrefix(Term<CommandParserState> original) {
        return wrapTermWithSemanticToken(original, TokenType.Companion.getTYPE());
    }
}
