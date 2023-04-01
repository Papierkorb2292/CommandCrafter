import * as vscode from 'vscode';
import {
    Disposable,
	DocumentFilter,
	LanguageClient,
	LanguageClientOptions,
	ServerOptions,
	StreamInfo,
	TransportKind
} from 'vscode-languageclient/node';
import * as net from 'net';

interface MinecraftConnectionType extends Disposable {

    getStreamInfo(): Promise<StreamInfo>;
}

export class SocketConnectionType implements MinecraftConnectionType {

    private readonly connection: net.Socket;

    constructor(address: string, port: number) {
        this.connection = net.connect({ host: address, port: port });
    }

    getStreamInfo() {
        return Promise.resolve({
            writer: this.connection,
            reader: this.connection
        });
    }

    dispose() {
        this.connection.destroy();
    }
}

export function runLanguageClient(context: vscode.ExtensionContext, connectionType: MinecraftConnectionType): LanguageClient {

    const languageClient = new LanguageClient(
		"CommandCrafter Language Client",
		() => connectionType.getStreamInfo(),
		{
            documentSelector: [{ pattern: "**" }],
            synchronize: {
                fileEvents: vscode.workspace.createFileSystemWatcher("**")
            }
        }
	);

    context.subscriptions.push(connectionType);
    context.subscriptions.push(languageClient.start());

    return languageClient;
}