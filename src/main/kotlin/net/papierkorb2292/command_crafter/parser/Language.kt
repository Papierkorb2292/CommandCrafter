package net.papierkorb2292.command_crafter.parser

import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.functions.FunctionBuilder
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.helper.RawResource

interface Language {
    fun parseToVanilla(
        reader: DirectiveStringReader<RawZipResourceCreator>,
        resource: RawResource
    )

    fun analyze(
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult
    )

    fun parseToCommands(
        reader: DirectiveStringReader<ParsedResourceCreator>,
        builder: FunctionBuilder<CommandSourceStack>
    ): FunctionDebugInformation?

    interface LanguageClosure {
        val startLanguage: Language
        fun endsClosure(reader: DirectiveStringReader<*>, skipNewLine: Boolean = true): Boolean
        fun skipClosureEnd(reader: DirectiveStringReader<*>, skipNewLine: Boolean = true)
    }

    class TopLevelClosure(override val startLanguage: Language) : LanguageClosure {
        override fun endsClosure(reader: DirectiveStringReader<*>, skipNewLine: Boolean): Boolean {
            return !reader.canRead()
        }

        override fun skipClosureEnd(reader: DirectiveStringReader<*>, skipNewLine: Boolean) { }
    }
}