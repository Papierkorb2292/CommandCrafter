package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

// This type is used instead of lsp class, to use own FileChangeType enum (lsp enum values don't match values for a vscode file system provider)
class FileEvent(val uri: String, val type: FileChangeType)