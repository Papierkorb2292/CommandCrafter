package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.Position

fun Position.advance() = advance(1)
fun Position.advance(amount: Int) = Position(line, character + amount)
