package net.papierkorb2292.command_crafter.editor.debugger.server

import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult

interface CommandResultContainer {
    var commandResult: CommandResult?
}