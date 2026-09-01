package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Suggestions.class)
public class SuggestionsMixin implements CompletionItemsContainer {

    @Shadow
    @Final
    private List<Suggestion> suggestions;
    @Nullable
    private List<? extends CompletionItem> command_crafter$completionItems;

    @Override
    public void command_crafter$setCompletionItem(@NotNull List<? extends CompletionItem> completionItems) {
        command_crafter$completionItems = completionItems;
    }

    @Nullable
    @Override
    public List<? extends CompletionItem> command_crafter$getCompletionItems() {
        return command_crafter$completionItems;
    }

    @ModifyExpressionValue(
            method = "create",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"
            )
    )
    private static Iterator<Suggestion> command_crafter$skipDeduplicationDuringAnalyzing(Iterator<Suggestion> iterator, @Share("suggestionsIterator") LocalRef<Iterator<Suggestion>> suggestionsIterator, @Share("isAnalyzing") LocalBooleanRef isAnalyzing) {
        // Suggestions don't need to be deduplicated when analyzing a command, because that's already done by VanillaLanguage
        if (getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) == null)
            return iterator;
        suggestionsIterator.set(iterator);
        isAnalyzing.set(true);
        return Collections.emptyIterator();
    }

    @WrapWithCondition(
            method = "create",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"
            )
    )
    private static boolean command_crafter$skipSortingDuringAnalyzingAndAddSuggestionsWithoutDeduplication(List<Suggestion> instance, Comparator<?> c, @Share("suggestionsIterator") LocalRef<Iterator<Suggestion>> suggestionsIterator, @Share("isAnalyzing") LocalBooleanRef isAnalyzing) {
        final var iterator = suggestionsIterator.get();
        if(iterator != null)
            while(iterator.hasNext())
                instance.add(iterator.next());
        // Suggestions don't need to be sorted when analyzing a command, because the editor can sort the completions
        return !isAnalyzing.get();
    }
}
