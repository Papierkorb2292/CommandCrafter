package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

class DeleteParams(
    var uri: String,
    var recursive: Boolean
) {
    constructor() : this("", false)
}