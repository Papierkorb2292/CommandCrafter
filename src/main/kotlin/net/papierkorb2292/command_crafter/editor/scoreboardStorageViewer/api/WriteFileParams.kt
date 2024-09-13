package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

class WriteFileParams(
    var uri: String,
    var contentBase64: String
) {
    constructor() : this("", "")
}