package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringEscaper
import org.eclipse.lsp4j.*

interface ColorInfo {
    val range: Range
    val color: Color
    fun getPresentation(params: ColorPresentationParams): List<ColorPresentation>
}

fun ColorInfo.withStringEscaper(escaper: StringEscaper) = object : ColorInfo {
    override val range: Range
        get() = this@withStringEscaper.range
    override val color: Color
        get() = this@withStringEscaper.color

    override fun getPresentation(params: ColorPresentationParams): List<ColorPresentation> =
        this@withStringEscaper.getPresentation(params).map { presentation ->
            val editColor = presentation.textEdit.newText ?: presentation.label
            val editRange = presentation.textEdit.range ?: range
            presentation.textEdit = TextEdit(editRange, escaper.escape(editColor))
            presentation
        }
}