package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

@JvmInline
value class FilePermission(val value: Int) {
    companion object {
        val READONLY = FilePermission(1)
    }
}