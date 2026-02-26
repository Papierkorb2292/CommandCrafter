package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.CompletionItem
import java.util.*

/**
 * This service is used by the vanilla language to analyze command arguments.
 * Implementations are discovered through a [java.util.ServiceLoader] and must
 * be specified in `resources/META-INF/services/net.papierkorb2292.command_crafter.editor.processing.command_arguments.CommandArgumentAnalyzerService`
 */
interface CommandArgumentAnalyzerService<TArgumentType : ArgumentType<*>> {
    companion object {
        val currentAnalyzingResult = ThreadLocal<AnalyzingResult>()

        private val analyzers: MutableMap<Class<out Any>, CommandArgumentAnalyzerService<*>> =
            ServiceLoader.load(CommandArgumentAnalyzerService::class.java)
                .flatMap { provider -> provider.argumentTypes.map { argumentClass -> argumentClass to provider } }
                .toMap(mutableMapOf())

        init {
            // Special fallback implementation for types without an analyzer
            analyzers[Any::class.java] = object : CommandArgumentAnalyzerService<ArgumentType<*>> {
                override val argumentTypes: List<Class<ArgumentType<*>>> = emptyList()

                override fun analyze(
                    context: CommandContext<SharedSuggestionProvider>,
                    type: ArgumentType<*>,
                    range: StringRange,
                    name: String,
                    reader: DirectiveStringReader<AnalyzingResourceCreator>,
                    buildContext: CommandBuildContext,
                    result: AnalyzingResult,
                ) {
                    result.semanticTokens.addMultiline(range, TokenType.PARAMETER, 0)
                }
            }
        }

        fun getAnalyzerForType(argumentClass: Class<out Any>): CommandArgumentAnalyzerService<*>? {
            val analyzer = analyzers[argumentClass]
            if(analyzer != null)
                return analyzer

            for(superInterface in argumentClass.interfaces) {
                val superAnalyzer = getAnalyzerForType(superInterface)
                if(superAnalyzer != null) {
                    analyzers[argumentClass] = superAnalyzer
                    return superAnalyzer
                }
            }
            val superAnalyzer = getAnalyzerForType(argumentClass.superclass ?: return null)
            analyzers[argumentClass] = superAnalyzer!!
            return superAnalyzer
        }
    }

    /**
     * The classes (or interfaces) of the argument types for which analyzing is provided (subclasses included)
     */
    val argumentTypes: List<Class<out TArgumentType>>

    /**
     * When analyzing a command argument, this method is called on an implementation with the
     * corresponding [argumentTypes]. [analyze] can leniently read from the [reader], can use the
     * argument parser's result from [context], and should populate the [AnalyzingResult] accordingly.
     *
     * The [reader]'s position can be modified and is
     * recommended to be at the end of the argument after the method returns. Its position
     * can be used by the caller to determine how far the lenient parser could read the argument.
     *
     * For convenience, this method also provides a [CommandBuildContext], so the implementation doesn't
     * have to get it from the ArgumentType
     */
    fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: TArgumentType,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    )

    fun hasCustomCompletions(context: CommandContext<SharedSuggestionProvider>, name: String): Boolean = false
    fun modifyVanillaCompletion(completion: CompletionItem) { }
}