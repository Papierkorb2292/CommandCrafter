package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.ParseContext;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.util.parsing.packrat.ErrorCollector;
import net.minecraft.util.parsing.packrat.ErrorEntry;
import net.minecraft.util.parsing.packrat.commands.CommandParserState;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.UnicodeNameSuggestionSupplier;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;
import static net.papierkorb2292.command_crafter.helper.UtilKt.runWithValueSwap;

@Mixin(Grammar.class)
public class GrammarMixin<S extends CommandParserState> {

    @WrapOperation(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/CommandParserState$Factory;create(Lnet/minecraft/commands/ParseContext;Ljava/lang/String;)Lnet/minecraft/util/parsing/packrat/commands/CommandParserState;"
            )
    )
    private S command_crafter$useDirectiveStringReader(CommandParserState.Factory<S> instance, ParseContext parseContext, String input, Operation<S> op) {
        final var fullInput = getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT());
        if (fullInput == null)
            return op.call(instance, parseContext, input);
        final var copy = fullInput.copy();
        copy.toCompleted();
        copy.setString(input);
        return instance.create(parseContext, copy.getString());
    }

    @ModifyArg(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/StringReaderParserState;<init>(Lnet/minecraft/util/parsing/packrat/ErrorCollector;Lnet/minecraft/util/parsing/packrat/commands/CommandParserState;)V"
            )
    )
    private ErrorCollector command_crafter$useMalformedParseErrorList(
            ErrorCollector original,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Share("malformedParseErrorList") LocalRef<MalformedParseErrorList> malformedParseErrorListRef
    ) {
        allowMalformedRef.set(PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed());
        if(allowMalformedRef.get()) {
            malformedParseErrorListRef.set(new MalformedParseErrorList());
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
    private List<ErrorEntry> command_crafter$getErrorsFromMalformedList(
            List<ErrorEntry> original,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Share("malformedParseErrorList") LocalRef<MalformedParseErrorList> malformedParseErrorListRef
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
    private <E> E command_crafter$updateSuggestionBuilderForCursor(
            E parseError,
            @Local(argsOnly = true) SuggestionsBuilder baseBuilder,
            @Share("allowMalformed") LocalBooleanRef allowMalformedRef,
            @Local(name = "offsetBuilder") LocalRef<SuggestionsBuilder> offsetBuilderRef
    ) {
        if(!allowMalformedRef.get()) return parseError;
        final var oldBuilder = offsetBuilderRef.get();
        final var errorCursor = ((ErrorEntry)parseError).cursor();
        if(oldBuilder.getStart() != errorCursor) {
            offsetBuilderRef.set(baseBuilder.createOffset(errorCursor));
            offsetBuilderRef.get().add(oldBuilder);
        }
        return parseError;
    }

    @WrapOperation(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/suggestions/SuggestionContext$Provider;getSuggestions(Lcom/mojang/brigadier/context/CommandContext;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$skipPossibleValuesForUnicodeNames(SuggestionContext.Provider instance, CommandContext<? extends SuggestionContext> commandContext, SuggestionsBuilder suggestionsBuilder, Operation<CompletableFuture<Suggestions>> op) {
        // Unicode suggestions are added separately to use SKIP_SUGGESTION_SORT_AND_DISTINCT
        if(instance instanceof UnicodeNameSuggestionSupplier)
            return CompletableFuture.completedFuture(null);
        return op.call(instance, commandContext, suggestionsBuilder);
    }

    @ModifyExpressionValue(
            method = "parseForSuggestions",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;buildFuture()Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$addUnicodeNameSuggestions(CompletableFuture<Suggestions> original, CommandContext<? extends SuggestionContext> context, @Local List<ErrorEntry> errors, @Local(name = "offsetBuilder") SuggestionsBuilder offsetBuilder) {
        final var unicodeSuggestions = errors.stream()
                .filter(error -> error.suggestion() instanceof UnicodeNameSuggestionSupplier)
                .map(error -> ((UnicodeNameSuggestionSupplier) error.suggestion()).getSuggestions(context, offsetBuilder.restart()))
                .toArray(CompletableFuture[]::new);
        if(unicodeSuggestions.length == 0)
            return original;
        return original.thenCombine(CompletableFuture.allOf(unicodeSuggestions), (suggestions, _) ->
                // Skip sorting and building hash set, because it takes a lot of time for unicode suggestions
                // Suggestions.create has to be used directly instead of Suggestions.merge, because the later also builds a hash set and would mess up the order
                runWithValueSwap(VanillaLanguage.Companion.getSKIP_SUGGESTION_SORT_AND_DISTINCT(),  true, () ->
                    Suggestions.create(offsetBuilder.getInput(),
                            Stream.concat(
                                    Stream.of(suggestions),
                                    Arrays.stream(unicodeSuggestions)
                                            .map(future -> (Suggestions)future.join())
                            ).flatMap(childSuggestions -> childSuggestions.getList().stream()).toList()
                )
        ));
    }
}
