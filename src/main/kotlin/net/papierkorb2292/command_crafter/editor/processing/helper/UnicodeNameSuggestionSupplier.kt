package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import it.unimi.dsi.fastutil.chars.CharSet
import it.unimi.dsi.fastutil.chars.CharSets
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.util.parsing.packrat.ParseState
import net.minecraft.util.parsing.packrat.SuggestionSupplier
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

object UnicodeNameSuggestionSupplier : SuggestionSupplier<StringReader> {
    private val allowedNameChars = CharSet.of(
        *(charSetFromToInclusive('a', 'z')
                    + charSetFromToInclusive('A', 'Z')
                    + charSetFromToInclusive('0', '9')
                    + CharSets.singleton('-')
            ).toCharArray()) // From SnbtGrammar.UNICODE_NAME
    private val characters: List<Pair<String, Message>> by lazy {
        (Character.MIN_CODE_POINT..Character.MAX_CODE_POINT)
            .asSequence()
            .map { Character.getName(it) to LiteralMessage(Character.toString(it)) }
            .filter { it.first != null && it.first.all { c -> c in allowedNameChars } }
            .toMutableList()
            .apply { sortBy { it.first } } // Sort now so Minecraft doesn't have to do so repeatedly
    }

    override fun possibleValues(parseState: ParseState<StringReader>): Stream<String> =
        characters.stream().map { it.first }

    fun getSuggestions(suggestionsBuilder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        return CompletableFuture.supplyAsync {
            SharedSuggestionProvider.suggest(characters, suggestionsBuilder, Pair<String, Message>::first, Pair<String, Message>::second)
        }.thenCompose { it }
    }

    private fun charSetFromToInclusive(from: Char, to: Char) = CharSets.fromTo(from, (to.code + 1).toChar())
}