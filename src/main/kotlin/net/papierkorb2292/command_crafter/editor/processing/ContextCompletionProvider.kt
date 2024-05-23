package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import java.util.concurrent.CompletableFuture

interface ContextCompletionProvider {
    fun getCompletions(context: CommandContext<*>, fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<Suggestions>
}