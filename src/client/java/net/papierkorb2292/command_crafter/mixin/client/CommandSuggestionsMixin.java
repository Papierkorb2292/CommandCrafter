package net.papierkorb2292.command_crafter.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
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
                    target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$addCustomSuggestions(CommandDispatcher<ClientSuggestionProvider> instance, ParseResults<ClientSuggestionProvider> parseResults, int cursor, Operation<CompletableFuture<Suggestions>> op) {
        return op.call(instance, parseResults, cursor).thenCompose(vanillaSuggestions ->
                ClientCommandCrafter.INSTANCE.getCustomIngameSuggestions(parseResults.getReader().getString(), cursor).thenApply(customSuggestions -> {
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
