package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

class FileSystemWatchParams(
    var uri: String,
    var watcherId: Int,
    var recursive: Boolean,
    var excludes: Array<String>
) {
    constructor() : this("", 0, false, arrayOf())
}