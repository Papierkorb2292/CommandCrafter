package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

@JvmInline
value class FileType(val value: Int) {
    companion object {
        val UNKNOWN = FileType(0)
        val FILE = FileType(1)
        val DIRECTORY = FileType(2)
        val SYMBOLIC_LINK = FileType(64)
    }
}