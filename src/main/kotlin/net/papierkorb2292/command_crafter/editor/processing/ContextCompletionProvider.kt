package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator.MacroInput
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringContent
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.networking.COMPLETION_CONTEXT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.list
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.DiagnosticSeverity
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

interface ContextCompletionProvider {
    fun getFunctionCompletions(completionInfo: FunctionCompletionInfo): CompletableFuture<List<CompletionItem>>
    fun getMacroCompletions(completionInfo: MacroCompletionInfo): CompletableFuture<List<CompletionItem>>

    companion object {
        val FUNCTION_COMPLETION_INFO_PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.list(),
            FunctionCompletionInfo::completeFile,
            ByteBufCodecs.VAR_INT,
            FunctionCompletionInfo::absoluteCursor,
            COMPLETION_CONTEXT_PACKET_CODEC.optional(),
            FunctionCompletionInfo::completionContext.toOptional(),
        ) { completeFile, absoluteCursor, completionContext ->
            FunctionCompletionInfo(completeFile, absoluteCursor, completionContext.getOrNull())
        }

        // Does NOT preserve macro parser
        val MACRO_INPUT_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.list(),
            MacroInput::lines,
            ByteBufCodecs.BOOL,
            MacroInput::isTemplate,
            ByteBufCodecs.BOOL,
            MacroInput::hasTemplatePrefix,
            ByteBufCodecs.BOOL,
            MacroInput::addMissingVariablesError,
            ByteBufCodecs.INT.optional(),
            { input: MacroInput -> input.illegalChatCharactersSeverity?.value}.toOptional()
        ) { lines, isTemplate, hasTemplatePrefix, addMisingVariablesError, severityValue ->
            MacroInput(lines, listOf(VanillaLanguage.TopLevelMacroParser.VANILLA), isTemplate, hasTemplatePrefix, addMisingVariablesError, severityValue.map(DiagnosticSeverity::forValue).getOrNull())
        }

        val MACRO_COMPLETION_INFO_PACKET_CODEC = StreamCodec.composite(
            MACRO_INPUT_CODEC,
            MacroCompletionInfo::macroInput,
            ByteBufCodecs.VAR_INT,
            MacroCompletionInfo::absoluteCursor,
            StringContent.PACKET_CODEC,
            MacroCompletionInfo::stringContent,
            IntList.PACKET_CODEC,
            MacroCompletionInfo::macroTargetCursors,
            COMPLETION_CONTEXT_PACKET_CODEC.optional(),
            MacroCompletionInfo::completionContext.toOptional(),
        ) { macroInput, absoluteCursor, stringContent, macroTargetCursors, completionContext ->
            MacroCompletionInfo(macroInput, absoluteCursor, stringContent, macroTargetCursors, completionContext.getOrNull())
        }

        val COMPLETION_INFO_PACKET_CODEC = ByteBufCodecs.either(FUNCTION_COMPLETION_INFO_PACKET_CODEC, MACRO_COMPLETION_INFO_PACKET_CODEC)
    }

    data class FunctionCompletionInfo(val completeFile: List<String>, val absoluteCursor: Int, val completionContext: CompletionContext?)
    data class MacroCompletionInfo(val macroInput: MacroInput, val absoluteCursor: Int, val stringContent: StringContent, val macroTargetCursors: IntList, val completionContext: CompletionContext?)
}