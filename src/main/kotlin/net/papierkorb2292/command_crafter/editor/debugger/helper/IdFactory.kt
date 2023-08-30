package net.papierkorb2292.command_crafter.editor.debugger.helper

class IdFactory {
    private var nextId = 0

    fun nextId(): Int {
        return nextId++
    }

    fun reset() {
        nextId = 0
    }
}