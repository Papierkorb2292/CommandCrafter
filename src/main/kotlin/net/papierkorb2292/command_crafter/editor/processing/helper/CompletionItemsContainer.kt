package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.CompletionItem

interface CompletionItemsContainer {
    fun `command_crafter$setCompletionItem`(completionItems: List<CompletionItem>)
    fun `command_crafter$getCompletionItems`(): List<@JvmWildcard CompletionItem>?
}