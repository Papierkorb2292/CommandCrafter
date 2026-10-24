package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.EditorURI
import org.eclipse.lsp4j.Range

class SymbolLocationMap<TInfo> {
    val workspaceLocations: MutableMap<String, List<SymbolLocation<TInfo>>> = mutableMapOf()

    fun isEmpty() = workspaceLocations.isEmpty()

    fun tryRemoveEmptyWorkspaceLocation(uri: String): Boolean {
        val locations = workspaceLocations[uri] ?: return false
        if(locations.isNotEmpty())
            return false
        workspaceLocations.remove(uri)
        return true
    }

    fun removeWorkspaceLocation(uri: String): Boolean =
        workspaceLocations.remove(uri) != null

    class SymbolLocation<TInfo>(val rangeGetter: () -> Range, var extraInfo: TInfo)
}