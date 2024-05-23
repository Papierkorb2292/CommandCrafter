package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.suggestion.Suggestions;
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

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
}
