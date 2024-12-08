package net.papierkorb2292.command_crafter.editor.processing.partial_id_autocomplete

import com.mojang.brigadier.suggestion.Suggestions
import net.papierkorb2292.partial_id_autocomplete.PartialIdGenerator

class PartialIdGeneratorServiceImpl : PartialIdGeneratorService {
    override fun getCompleteSuggestions(originalSuggestions: Suggestions, currentInput: String): Suggestions
        = PartialIdGenerator(originalSuggestions).getCompleteSuggestions(currentInput)

    override fun areSuggestionsIds(suggestions: Suggestions)
        = PartialIdGenerator.areSuggestionsIds(suggestions)
}