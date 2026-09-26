package net.papierkorb2292.command_crafter.editor

class FindFilesRelativeParams(
    var baseUri: String,
    var pattern: String,
) {
    constructor() : this("", "")
}