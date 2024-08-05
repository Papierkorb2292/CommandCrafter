package net.papierkorb2292.command_crafter.editor.processing.partial_id_autocomplete

import com.mojang.brigadier.suggestion.Suggestions

interface PartialIdGeneratorService {
    fun getCompleteSuggestions(originalSuggestions: Suggestions, currentInput: String): Suggestions
    fun areSuggestionsIds(suggestions: Suggestions): Boolean
}