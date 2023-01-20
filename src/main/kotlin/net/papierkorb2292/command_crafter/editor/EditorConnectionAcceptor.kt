package net.papierkorb2292.command_crafter.editor

interface EditorConnectionAcceptor {

    fun accept(): EditorConnection
    fun isRunning(): Boolean
}