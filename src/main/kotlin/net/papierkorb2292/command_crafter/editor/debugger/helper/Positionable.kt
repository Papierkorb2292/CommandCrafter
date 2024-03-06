package net.papierkorb2292.command_crafter.editor.debugger.helper

interface Positionable {
    val line: Int
    val char: Int?
    fun setPos(line: Int, char: Int?)
}