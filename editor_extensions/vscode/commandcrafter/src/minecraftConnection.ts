import * as vscode from 'vscode';
import {
    Disposable,
	LanguageClient,
	State,
	StreamInfo
} from 'vscode-languageclient/node';
import * as net from 'net';
import { MinecraftConsole } from './minecraftConsole';
import { LanguageClientRunner } from './extension';

interface MinecraftConnectionType extends Disposable {

    connect(): Promise<StreamInfo>;
}

export class SocketConnectionType implements MinecraftConnectionType {

    connection: net.Socket | null = null;

    constructor(public address: string, public port: number) { }

    connect() {
        this.connection = net.connect({ host: this.address, port: this.port });
        return Promise.resolve({
            writer: this.connection,
            reader: this.connection
        });
    }

    dispose() {
        this.connection?.destroy();
    }
}

export class MinecraftLanguageClientRunner implements Disposable, LanguageClientRunner {

    languageClient?: LanguageClient | null;
    prevOutputChannel?: vscode.OutputChannel | null;
    clientState = State.Stopped;

    connectionFeatures: ConnectionFeature[] = [ ];

    constructor(private connectionType: MinecraftConnectionType, context: vscode.ExtensionContext) {
        this.connectionFeatures.push(new MinecraftConsole(context, "commandcrafter.console", this));
        context.subscriptions.push(this);
    }

    setConnectionType(connectionType: MinecraftConnectionType) {
        this.stopLanguageClient();

        this.connectionType.dispose();
        this.connectionType = connectionType;
    }

    startLanguageClient(): LanguageClient {
        this.prevOutputChannel?.dispose();
        const languageClient = new LanguageClient(
    		"CommandCrafter Language Client",
    		() => this.connectionType.connect(),
    		{
                documentSelector: [{ pattern: "**" }],
                synchronize: {
                    fileEvents: vscode.workspace.createFileSystemWatcher("**")
                }
            }
        );

        this.prevOutputChannel = languageClient.outputChannel;

        languageClient.onDidChangeState((e) => {
            this.clientState = e.newState;
            switch(e.newState) {
                case State.Starting:
                    this.connectionFeatures.forEach(feature => feature.onLanguageClientStart(languageClient));
                    break;
                case State.Running:
                    this.connectionFeatures.forEach(feature => feature.onLanguageClientReady(languageClient));
                    break;
                case State.Stopped:
                    this.connectionFeatures.forEach(feature => feature.onLanguageClientStop());
                    break;
            }
        });

        languageClient.start();
        this.languageClient = languageClient;
        return languageClient;
    }

    stopLanguageClient(): void {
        let languageClient = this.languageClient;
        this.languageClient = null;
        languageClient?.stop();
    }

    dispose() {
        this.connectionType.dispose();
    }
}

export interface ConnectionFeature {
    onLanguageClientStart(languageClient: LanguageClient): void;
    onLanguageClientReady(languageClient: LanguageClient): void;
    onLanguageClientStop(): void;
}