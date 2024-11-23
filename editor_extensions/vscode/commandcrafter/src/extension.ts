import * as vscode from 'vscode';
import { AddressConfigMalformedError, MinecraftConnectionType, MinecraftLanguageClientRunner, parseAddressConfig, SocketConnectionType } from './minecraftConnection';
import { State } from 'vscode-languageclient';5

let prevMinecraftAddress: string | undefined
let minecraftLanguageClientRunner: MinecraftLanguageClientRunner | undefined

export function activate(context: vscode.ExtensionContext) {
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
	const addressConfig = vscode.workspace.getConfiguration("CommandCrafter").get<string>("MinecraftAddress")
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
	startLanguageClient(): void;
	stopLanguageClient(): void;
}

export function findFiles(filePattern: string): Thenable<string[]> {
	return vscode.workspace.findFiles(filePattern, null).then((uris) => {
		return uris.map(uri => uri.toString());
	});
}

export function getFeatureConfig(): FeatureConfig | undefined {
	return vscode.workspace.getConfiguration("CommandCrafter").get<FeatureConfig>("FeatureConfig")
}

export type FeatureConfig = { [key: string]: string }