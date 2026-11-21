package net.papierkorb2292.command_crafter.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SuggestionBox;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import net.papierkorb2292.command_crafter.editor.processing.command_arguments.ResourceOrIdArgumentAnalyzer;
import net.papierkorb2292.command_crafter.helper.UtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    @WrapOperation(
            method = "updateCommandInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/SuggestionBox$Result;computeSuggestions(I)Ljava/util/concurrent/CompletableFuture;"
            ),
            order = 2000 // Apply after partial id autocomplete, since we add our own partial id suggestions
    )
    private CompletableFuture<Suggestions> command_crafter$addCustomSuggestions(SuggestionBox.Result instance, int cursorPosition, Operation<CompletableFuture<Suggestions>> op) {
        final var vanillaFuture = UtilKt.runWithValueSwap(
                ResourceOrIdArgumentAnalyzer.Companion.getShouldSkipResourceOrIdSuggestions(),
                true,
                () -> op.call(instance, cursorPosition)
        );
        return vanillaFuture.thenCompose(vanillaSuggestions ->
                ClientCommandCrafter.INSTANCE.getCustomIngameSuggestions(instance.parseResults().getReader().getString(), cursorPosition).thenApply(customSuggestions -> {
                    if(vanillaSuggestions.isEmpty()) return customSuggestions;
                    if(customSuggestions.isEmpty()) return vanillaSuggestions;
                    final var suggestionList = new ArrayList<Suggestion>();
                    // Add custom suggestions before vanilla suggestions, so they are shown above packrat suggestions and such
                    suggestionList.addAll(customSuggestions.getList());
                    suggestionList.addAll(vanillaSuggestions.getList());
                    return new Suggestions(
                            StringRange.encompassing(vanillaSuggestions.getRange(), customSuggestions.getRange()),
                            suggestionList
                    );
                })
        );
    }

    @ModifyReceiver(
            method = "updateCommandInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$ensureCompletionFutureRunsOnRenderThread(CompletableFuture<Suggestions> instance, Consumer<?> consumer) {
        // This is necessary, because UnicodeNameSuggestionSupplier could have switched the future to a different thread,
        // which would throw an error in showSuggestions at this.font.width
        return instance.thenApplyAsync(Function.identity(), Minecraft.getInstance());
    }
}
