package net.papierkorb2292.command_crafter.editor.processing.symbols

import org.eclipse.lsp4j.Position
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

    data class SymbolLocation<TInfo>(val range: Range, val selectionRangeOverride: Range?, var extraInfo: TInfo) {
        inline fun mapPositions(mapper: (Position) -> Position): SymbolLocation<TInfo> {
            val newRange = Range(mapper(range.start), mapper(range.end))
            val newSelectionRangeOverride = selectionRangeOverride?.let { Range(mapper(it.start), mapper(it.end)) }
            if(newRange == range && newSelectionRangeOverride == selectionRangeOverride)
                return this
            return copy(range = newRange, selectionRangeOverride = newSelectionRangeOverride)
        }
    }
}