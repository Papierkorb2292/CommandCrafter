import * as vscode from 'vscode';
import { AddressConfigMalformedError, MinecraftConnectionType, MinecraftLanguageClientRunner, parseAddressConfig, SocketConnectionType } from './minecraftConnection';
import { State } from 'vscode-languageclient';
import { activateLog, outputChannel } from './extensionLog';
import { getMinecraftAddress } from './settings';

let prevMinecraftAddress: string | undefined
let minecraftLanguageClientRunner: MinecraftLanguageClientRunner | undefined

export function activate(context: vscode.ExtensionContext) {
	activateLog(context)
	startMinecraftLanguageClientRunner(context)
	context.subscriptions.push(vscode.commands.registerCommand('commandcrafter.activate', () => { }));
}

export function startMinecraftLanguageClientRunner(context: vscode.ExtensionContext) {
	const minecraftConnection = getUpdatedMinecraftConnectionType()
	minecraftLanguageClientRunner = new MinecraftLanguageClientRunner(minecraftConnection, context);
	minecraftLanguageClientRunner.startLanguageClient();
	
}

export function checkUpdateMinecraftAddress() {
	const updatedConnectionType = getUpdatedMinecraftConnectionType()
	if(updatedConnectionType !== null) {
		minecraftLanguageClientRunner!!.setConnectionType(updatedConnectionType)
	}
}

function getUpdatedMinecraftConnectionType(): MinecraftConnectionType | null {
	const addressConfig = getMinecraftAddress()
	if(addressConfig == prevMinecraftAddress)
		return null
	if(!addressConfig)
		return null
	prevMinecraftAddress = addressConfig
	const minecraftConnection = parseAddressConfig(addressConfig)
	if(minecraftConnection instanceof AddressConfigMalformedError) {
		vscode.window.showErrorMessage(minecraftConnection.message)
		return null
	}
	return minecraftConnection
}

export function deactivate() {}

export interface LanguageClientRunner {
	clientState: State
	extensionVersion: string
	startLanguageClient(): void;
	stopLanguageClient(): void;
}

export function findFiles(filePattern: string): Thenable<string[]> {
	outputChannel?.appendLine("Searching for files  '" + filePattern + "' in " + vscode.workspace.workspaceFolders?.map(folder => folder.uri.toString()))
	return vscode.workspace.findFiles(filePattern, null).then((uris) => {
		const result = uris.map(uri => uri.toString());
		outputChannel?.appendLine("Result: " + result);
		return result;
	});
}

export function fileExists(file: string | vscode.Uri): Thenable<boolean> {
	if(typeof(file) === "string") {
		file = vscode.Uri.parse(file)
	}
	return vscode.workspace.fs.stat(file).then(() => true, () => false);
}