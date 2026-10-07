package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.EditorURI
import org.eclipse.lsp4j.Range

class SymbolLocationMap<TInfo> {
    val workspaceLocations: MutableMap<EditorURI, List<SymbolLocation<TInfo>>> = mutableMapOf()

    fun isEmpty() = workspaceLocations.isEmpty()

    fun tryRemoveEmptyWorkspaceLocation(uri: EditorURI): Boolean {
        val locations = workspaceLocations[uri] ?: return false
        if(locations.isNotEmpty())
            return false
        workspaceLocations.remove(uri)
        return true
    }

    class SymbolLocation<TInfo>(val rangeGetter: () -> Range, val extraInfo: TInfo)
}