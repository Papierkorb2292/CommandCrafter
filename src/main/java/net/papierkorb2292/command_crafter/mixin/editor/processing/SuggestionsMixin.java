package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.brigadier.suggestion.Suggestions;
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Comparator;
import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Suggestions.class)
public class SuggestionsMixin implements CompletionItemsContainer {

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

    @WrapWithCondition(
            method = "create",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"
            )
    )
    private static boolean command_crafter$skipSortingDuringAnalyzing(List<?> instance, Comparator<?> c) {
        // Suggestions don't need to be sorted when analyzing an command, because the editor can sort the completions
        return getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) == null;
    }
}
