package net.papierkorb2292.command_crafter;

import net.papierkorb2292.command_crafter.editor.console.ConsoleCommand;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

/**
 * Defines additional functions for the MinecraftLanguageServer that can be invoked via LSP.
 * They have to be defined in a java class, because LSP4J requires them to return void.
 */
public interface MinecraftLanguageServerExtension {
    @JsonNotification
    void runCommand(ConsoleCommand command);
}
