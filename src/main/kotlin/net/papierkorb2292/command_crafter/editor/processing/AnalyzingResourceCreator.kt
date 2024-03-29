package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import java.util.*

class AnalyzingResourceCreator(val languageServer: MinecraftLanguageServer, val sourceFunctionUri: String) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()

    constructor(
        languageServer: MinecraftLanguageServer,
        sourceFunctionUri: String,
        topLevelAnalyzingResult: AnalyzingResult,
    ): this(languageServer, sourceFunctionUri) {
        resourceStack.push(ResourceStackEntry(topLevelAnalyzingResult))
    }

    constructor(
        languageServer: MinecraftLanguageServer,
        sourceFunctionUri: String,
        resourceCreator: AnalyzingResourceCreator,
    ): this(languageServer, sourceFunctionUri) {
        resourceStack.clear()
        resourceStack.addAll(resourceCreator.resourceStack)
    }

    data class ResourceStackEntry(val analyzingResult: AnalyzingResult)
}