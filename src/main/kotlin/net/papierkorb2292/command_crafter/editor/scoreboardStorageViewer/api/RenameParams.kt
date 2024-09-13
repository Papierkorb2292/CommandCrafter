package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

class RenameParams(
    var oldUri: String,
    var newUri: String,
    var overwrite: Boolean
) {
    constructor() : this("", "", false)
}