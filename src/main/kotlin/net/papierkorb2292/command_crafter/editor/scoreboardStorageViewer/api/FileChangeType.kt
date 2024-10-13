package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

// This type is used instead of the lsp enum, because the lsp enum values don't match values for a vscode file system provider
enum class FileChangeType(val value: Int) {
    Changed(1),
    Created(2),
    Deleted(3);

    companion object {
        fun fromInt(value: Int) = when(value) {
            1 -> Changed
            2 -> Created
            3 -> Deleted
            else -> throw IllegalArgumentException("Unknown FileChangeType: $value")
        }
    }
}