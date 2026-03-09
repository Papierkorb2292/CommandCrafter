package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.util.parsing.packrat.ErrorCollector;
import net.minecraft.util.parsing.packrat.ErrorEntry;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.UnicodeNameSuggestionSupplier;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Grammar.class)
public class GrammarMixin {

    @ModifyVariable(
            method = "parseForSuggestions",
            at = @At("STORE")
    )
    private StringReader command_crafter$useDirectiveStringReader(StringReader original) {
        final var fullInput = getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT());
        if (fullInput == null)
            return original;
        fullInput.toCompleted();
        fullInput.setString(original.getString());
        return fullInput;
    }

    @ModifyArg(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderParserState;<init>(Lnet/minecraft/util/parsing/packrat/ErrorCollector;Lcom/mojang/brigadier/StringReader;)V"
            )
    )
    private ErrorCollector<StringReader> command_crafter$useMalformedParseErrorList(
            ErrorCollector<StringReader> original,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Share("malformedParseErrorList") LocalRef<MalformedParseErrorList<StringReader>> malformedParseErrorListRef
    ) {
        allowMalformedRef.set(PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed());
        if(allowMalformedRef.get()) {
            malformedParseErrorListRef.set(new MalformedParseErrorList<>());
            original.finish(0); // This is necessary, because the cursor is at -1 by default, but it is used to index into a string later in the method
            return malformedParseErrorListRef.get();
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/ErrorCollector$LongestOnly;entries()Ljava/util/List;"
            )
    )
    private List<ErrorEntry<StringReader>> command_crafter$getErrorsFromMalformedList(
            List<ErrorEntry<StringReader>> original,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Share("malformedParseErrorList") LocalRef<MalformedParseErrorList<StringReader>> malformedParseErrorListRef
    ) {
        return allowMalformedRef.get() ? malformedParseErrorListRef.get().getErrors() : original;
    }

    @ModifyExpressionValue(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;"
            )
    )
    private Object command_crafter$updateSuggestionBuilderForCursor(
            Object parseError,
            SuggestionsBuilder baseBuilder,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Local(ordinal = 1) LocalRef<SuggestionsBuilder> suggestionsBuilderRef
    ) {
        if(!allowMalformedRef.get()) return parseError;
        final var oldBuilder = suggestionsBuilderRef.get();
        final var errorCursor = ((ErrorEntry<?>)parseError).cursor();
        if(oldBuilder.getStart() != errorCursor) {
            suggestionsBuilderRef.set(baseBuilder.createOffset(errorCursor));
            suggestionsBuilderRef.get().add(oldBuilder);
        }
        return parseError;
    }

    @WrapOperation(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/SuggestionSupplier;possibleValues(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/util/stream/Stream;"
            )
    )
    private Stream<String> command_crafter$skipPossibleValuesForUnicodeNames(SuggestionSupplier<StringReader> instance, ParseState<StringReader> state, Operation<Stream<String>> op) {
        if(instance instanceof UnicodeNameSuggestionSupplier)
            return Stream.of();
        return op.call(instance, state);
    }

    @ModifyExpressionValue(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;buildFuture()Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$addUnicodeNameSuggestions(CompletableFuture<Suggestions> original, SuggestionsBuilder suggestionsBuilder, @Local List<ErrorEntry<StringReader>> errors) {
        final var unicodeSuggestions = errors.stream()
                .filter(error -> error.suggestions() instanceof UnicodeNameSuggestionSupplier)
                .map(error -> ((UnicodeNameSuggestionSupplier) error.suggestions()).getSuggestions(suggestionsBuilder.createOffset(error.cursor())))
                .toArray(CompletableFuture[]::new);
        if(unicodeSuggestions.length == 0)
            return original;
        return original.thenCombine(CompletableFuture.allOf(unicodeSuggestions), (suggestions, _void) ->
                Suggestions.merge(suggestionsBuilder.getInput(),
                    Stream.concat(
                            Stream.of(suggestions),
                            Arrays.stream(unicodeSuggestions)
                                .map(future -> (Suggestions)future.join())
                    ).toList()
        ));
    }
}
