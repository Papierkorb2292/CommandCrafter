package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.CombinedCompletionItemProvider
import net.papierkorb2292.command_crafter.editor.processing.SimpleCompletionItemProvider
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import org.eclipse.lsp4j.*

class DirectiveManager {
    companion object {
        val DIRECTIVES = FabricRegistryBuilder.createSimple<DirectiveType>(RegistryKey.ofRegistry(Identifier.of("command_crafter", "directives"))).buildAndRegister()!!
    }

    fun readDirective(reader: DirectiveStringReader<*>) {
        val directive = reader.readUnquotedString()
        reader.expect(' ')
        (DIRECTIVES.get(Identifier.of(directive))
            ?: throw IllegalArgumentException("Error while parsing function: Encountered unknown directive '$directive' on line ${reader.currentLine}"))
            .read(reader)
        if(!reader.canRead()) return
        if(reader.peek() != '\n') {
            throw IllegalArgumentException("Error while parsing function: Expected newline after directive on line ${reader.currentLine}")
        }
        reader.skip()
    }

    fun readDirectiveAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult) {
        fun endDirective() {
            if(!reader.canRead()) return
            if(reader.peek() != '\n') {
                val endPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                analyzingResult.diagnostics += Diagnostic(
                    Range(endPos, endPos.advance()),
                    "Expected newline after directive"
                )
                while(reader.canRead() && reader.peek() != '\n')
                    reader.skip()
                if(!reader.canRead())
                    return
            }
            reader.skip()
        }

        val directiveStartCursor = reader.cursor - 1 // Subtract 1 to include '@'
        val directiveStartPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor - 1, reader.lines)
        val id = try {
            Identifier.fromCommandInput(reader)
        } catch(e: CommandSyntaxException) {
            analyzingResult.diagnostics += Diagnostic(
                Range(
                    directiveStartPos,
                    AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                ),
                e.message
            )
            endDirective()
            return
        }
        val directiveEndCursor = reader.cursor
        val directiveEndPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
        analyzingResult.semanticTokens.add(directiveStartPos.line, directiveStartPos.character, directiveEndCursor - directiveStartCursor, TokenType.STRUCT, 0)

        suggestDirectives(StringRange(directiveStartCursor, directiveEndCursor), analyzingResult, true)

        if(!reader.canRead() || reader.peek() != ' ') {
            analyzingResult.diagnostics += Diagnostic(
                Range(directiveEndPos, directiveEndPos.advance()),
                "Expected ' '"
            )
            endDirective()
            return
        }
        reader.skip()
        val directiveType = DIRECTIVES.get(id)
        if(directiveType == null) {
            analyzingResult.diagnostics += Diagnostic(
                Range(directiveStartPos, directiveEndPos),
                "Error while parsing function: Encountered unknown directive '$id' on line ${reader.currentLine}"
            )
            endDirective()
            return
        }
        directiveType.readAndAnalyze(reader, analyzingResult)
        endDirective()
    }

    /**
     * Suggests to insert directives at the given range. The completions only insert the directive at the cursor position
     * and don't replace any text.
     */
    fun suggestDirectives(range: StringRange, analyzingResult: AnalyzingResult, replaceRange: Boolean = false) {
        analyzingResult.addCompletionProviderWithContinuosMapping(
            AnalyzingResult.DIRECTIVE_COMPLETION_CHANNEL,
            AnalyzingResult.RangedDataProvider(range, CombinedCompletionItemProvider(
                DIRECTIVES.ids.map {
                    SimpleCompletionItemProvider(
                        "@" + it.toShortString(),
                        range.start,
                        { if(replaceRange) range.end else null },
                        analyzingResult.mappingInfo.copy(),
                    )
                }
            ))
        )
    }

    interface DirectiveType {
        fun read(reader: DirectiveStringReader<*>)
        fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult)
    }
}
