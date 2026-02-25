package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Range

interface ColorInfo {
    val range: Range
    val color: Color
    fun getPresentation(params: ColorPresentationParams): List<ColorPresentation>
}