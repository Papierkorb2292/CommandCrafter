package net.papierkorb2292.command_crafter.parser

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
        val directive = reader.readUnquotedString()
        if(!reader.canRead() || reader.peek() != ' ') {
            val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
            analyzingResult.diagnostics += Diagnostic(
                Range(pos, pos.advance()),
                "Expected ' '"
            )
            return
        }
        val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
        reader.skip()
        val directiveType = DIRECTIVES.get(Identifier.of(directive))
        if(directiveType == null) {
            analyzingResult.diagnostics += Diagnostic(
                Range(pos.advance(-directive.length-1), pos),
                "Error while parsing function: Encountered unknown directive '$directive' on line ${reader.currentLine}"
            )
            return
        }
        analyzingResult.semanticTokens.add(pos.line, pos.character - directive.length - 1, directive.length + 1, TokenType.STRUCT, 0)
        directiveType.readAndAnalyze(reader, analyzingResult)
        if(!reader.canRead()) return
        if(reader.peek() != '\n') {
            val endPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
            analyzingResult.diagnostics += Diagnostic(
                Range(endPos, endPos.advance()),
                "Expected newline after directive"
            )
        }
        reader.skip()
    }

    interface DirectiveType {
        fun read(reader: DirectiveStringReader<*>)
        fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult)
    }
}
