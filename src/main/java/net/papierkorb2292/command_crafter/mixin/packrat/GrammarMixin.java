package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.ErrorEntry;
import net.minecraft.util.parsing.packrat.ErrorCollector;
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(Grammar.class)
public class GrammarMixin {

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
}
