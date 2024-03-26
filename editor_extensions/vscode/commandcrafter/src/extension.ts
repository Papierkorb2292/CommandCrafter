import * as vscode from 'vscode';
import { MinecraftLanguageClientRunner, SocketConnectionType } from './minecraftConnection';
import { State } from 'vscode-languageclient';

export function activate(context: vscode.ExtensionContext) {
	let minecraftLanguageClientRunner = new MinecraftLanguageClientRunner(new SocketConnectionType("localhost", 52853), context);
	minecraftLanguageClientRunner.startLanguageClient();

	context.subscriptions.push(vscode.commands.registerCommand('commandcrafter.activate', () => { }));
}

export function deactivate() {}

export interface LanguageClientRunner {
	clientState: State
	startLanguageClient(): void;
	stopLanguageClient(): void;
}

export function findFiles(filePattern: string): Thenable<string[]> {
	return vscode.workspace.findFiles(filePattern, null).then((uris) => {
		return uris.map(uri => uri.toString());
	});
}