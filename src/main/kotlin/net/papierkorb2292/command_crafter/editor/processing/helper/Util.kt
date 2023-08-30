package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import org.eclipse.lsp4j.Position

fun Position.advance() = advance(1)
fun Position.advance(amount: Int) = Position(line, character + amount)

operator fun Int.compareTo(range: StringRange): Int {
    if(this < range.start) return -1
    if(this > range.end) return 1
    return 0
}

operator fun StringRange.compareTo(range: StringRange): Int {
    if(this.end < range.start) return -1
    if(this.start > range.end) return 1
    return 0
}