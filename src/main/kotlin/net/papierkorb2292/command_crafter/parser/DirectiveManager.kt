package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Range

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

        val directiveStartCursor = reader.absoluteCursor - 1 // Subtract 1 to include '@'
        val directiveStartPos = AnalyzingResult.getPositionFromCursor(directiveStartCursor, reader.lines)
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
        val directiveEndCursor = reader.absoluteCursor
        val directiveEndPos = AnalyzingResult.getPositionFromCursor(directiveEndCursor, reader.lines)
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
        analyzingResult.semanticTokens.add(directiveStartPos.line, directiveStartPos.character, directiveEndCursor - directiveStartCursor, TokenType.STRUCT, 0)
        directiveType.readAndAnalyze(reader, analyzingResult)
        endDirective()
    }

    interface DirectiveType {
        fun read(reader: DirectiveStringReader<*>)
        fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult)
    }
}
