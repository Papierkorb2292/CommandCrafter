import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import { runLanguageClient, SocketConnectionType } from './minecraftConnection';

let client: LanguageClient | null = null;

export function activate(context: vscode.ExtensionContext) {
	client = runLanguageClient(context, new SocketConnectionType("localhost", 52853));

}

export function deactivate() {}
