package net.papierkorb2292.command_crafter.editor.processing.partial_id_autocomplete

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import net.fabricmc.loader.api.FabricLoader
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.InsertReplaceEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*

object CompletionItemsPartialIdGenerator {
    private val partialIdGeneratorService: PartialIdGeneratorService by lazy {
        ServiceLoader.load(PartialIdGeneratorService::class.java).first()
    }

    fun addPartialIdsToCompletionItems(completionItems: List<CompletionItem>, currentInput: String): List<CompletionItem> {
        if(
            !FabricLoader.getInstance().isModLoaded("partial_id_autocomplete")
            || completionItems.isEmpty()
            || completionItems.any { it.textEdit == null }
            )
            return completionItems
        val dummyRange = StringRange(0, 0)
        val suggestions = Suggestions(dummyRange, completionItems.map { item ->
            Suggestion(dummyRange, item.textEdit.map({ it.newText }, { it.newText } ))
        })
        if(!partialIdGeneratorService.areSuggestionsIds(suggestions))
            return completionItems
        val completeSuggestions = partialIdGeneratorService.getCompleteSuggestions(suggestions, currentInput)
        val partialSuggestions = completeSuggestions.list.subList(0, completeSuggestions.list.size - suggestions.list.size)
        val templateCompletion = completionItems.first()
        return partialSuggestions.map { suggestion ->
            val completionItem = CompletionItem()
            completionItem.label = suggestion.text
            completionItem.textEdit = templateCompletion.textEdit.map(
                { Either.forLeft(TextEdit(it.range, suggestion.text)) },
                { Either.forRight(InsertReplaceEdit(suggestion.text, it.insert, it.replace)) }
            )
            //Put at beginning
            completionItem.sortText = " " + suggestion.text
            completionItem
        } + completionItems
    }
}