package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Mixin(ObjectiveCriteriaArgument.class)
public class ObjectiveCriteriaArgumentMixin {

    private List<String> command_crafter$cachedSuggestions;

    @WrapMethod(
            method = "listSuggestions"
    )
    private <S> CompletableFuture<Suggestions> command_crafter$cacheSuggestions(CommandContext<S> context, SuggestionsBuilder builder, Operation<CompletableFuture<Suggestions>> original) throws ExecutionException, InterruptedException {
        if (command_crafter$cachedSuggestions == null) {
            final var suggestions = original.call(context, new SuggestionsBuilder("", 0)).get().getList();
            List<String> list = new ArrayList<>(suggestions.size());
            for (Suggestion suggestion : suggestions) {
                list.add(suggestion.getText());
            }
            command_crafter$cachedSuggestions = list;
        }
        return SharedSuggestionProvider.suggest(command_crafter$cachedSuggestions, builder);
    }
}
