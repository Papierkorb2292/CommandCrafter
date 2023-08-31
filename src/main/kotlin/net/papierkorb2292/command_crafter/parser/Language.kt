package net.papierkorb2292.command_crafter.parser

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.helper.RawResource

interface Language {
    fun parseToVanilla(
        reader: DirectiveStringReader<RawZipResourceCreator>,
        source: ServerCommandSource,
        resource: RawResource
    )

    fun analyze(
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        source: ServerCommandSource,
        result: AnalyzingResult
    )

    fun parseToCommands(
        reader: DirectiveStringReader<ParsedResourceCreator?>,
        source: ServerCommandSource,
    ): Pair<List<CommandFunction.Element>, FunctionDebugInformation?>

    interface LanguageClosure {
        val startLanguage: Language
        fun endsClosure(reader: DirectiveStringReader<*>): Boolean
        fun skipClosureEnd(reader: DirectiveStringReader<*>)
    }

    class TopLevelClosure(override val startLanguage: Language) : LanguageClosure {
        override fun endsClosure(reader: DirectiveStringReader<*>): Boolean {
            return !reader.canRead()
        }

        override fun skipClosureEnd(reader: DirectiveStringReader<*>) { }
    }
}