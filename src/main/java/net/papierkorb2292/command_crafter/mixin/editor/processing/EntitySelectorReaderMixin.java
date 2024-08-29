package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.EntitySelectorReader;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Mixin(EntitySelectorReader.class)
public class EntitySelectorReaderMixin implements AnalyzingResultDataContainer {

    @Shadow @Final private StringReader reader;
    @Shadow private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestionProvider;

    private AnalyzingResult command_crafter$analyzingResult = null;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Override
    public AnalyzingResult command_crafter$getAnalyzingResult() {
        return command_crafter$analyzingResult;
    }

    @Inject(
            method = "readArguments",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;getHandler(Lnet/minecraft/command/EntitySelectorReader;Ljava/lang/String;I)Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;"
            )
    )
    private void command_crafter$highlightOptionName(CallbackInfo ci, @Local String name, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, name.length(), TokenType.Companion.getPROPERTY(), 0);
        }
    }

    @Inject(
            method = "readAtVariable",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    ordinal = 1,
                    remap = false
            )
    )
    private void command_crafter$highlightAt(CallbackInfo ci) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(reader.getCursor() - 2, 2, TokenType.Companion.getCLASS(), 0);
        }
    }

    @Inject(
            method = "readRegular",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/UUID;fromString(Ljava/lang/String;)Ljava/util/UUID;"
            )
    )
    private void command_crafter$highlightRegular(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> command_crafter$lastOptionSuggestionProvider = null;
    private int command_crafter$lastOptionStartCursor = -1;

    @Inject(
            method = "setSuggestionProvider",
            at = @At("HEAD")
    )
    private void command_crafter$saveLastOptionSuggestionProvider(BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> provider, CallbackInfo ci) {
        command_crafter$lastOptionSuggestionProvider = provider;
        command_crafter$lastOptionStartCursor = reader.getCursor();
    }

    @ModifyReturnValue(
            method = "listSuggestions",
            at = @At("RETURN")
    )
    private CompletableFuture<Suggestions> command_crafter$addLastOptionSuggestions(CompletableFuture<Suggestions> original, SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        if(command_crafter$lastOptionSuggestionProvider == null || command_crafter$lastOptionSuggestionProvider == suggestionProvider)
            return original;
        final var lastSuggestions = command_crafter$lastOptionSuggestionProvider.apply(builder.createOffset(command_crafter$lastOptionStartCursor), consumer);
        return CompletableFuture.allOf(original, lastSuggestions).thenApply(v ->
                Suggestions.merge(builder.getInput(), ImmutableList.of(original.join(), lastSuggestions.join()))
        );
    }
}
