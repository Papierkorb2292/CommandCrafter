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
import { DebugClient } from './debugClient';

export interface MinecraftConnectionType extends Disposable {

    connect(): Promise<StreamInfo>;
    copy(): MinecraftConnectionType;
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

    copy() {
        return new SocketConnectionType(this.address, this.port);
    }
}

export class MinecraftLanguageClientRunner implements Disposable, LanguageClientRunner {

    languageClient?: LanguageClient | null;
    prevOutputChannel?: vscode.OutputChannel | null;
    clientState = State.Stopped;

    connectionFeatures: ConnectionFeature[] = [ ];

    constructor(private connectionType: MinecraftConnectionType, context: vscode.ExtensionContext) {
        this.connectionFeatures.push(new MinecraftConsole(context, "commandcrafter.console", this));
        this.connectionFeatures.push(new DebugClient(context, "commandcrafter", this))
        context.subscriptions.push(this);
        context.subscriptions.push(
            vscode.commands.registerCommand('commandcrafter.toggleLanguageClient', () => {
                if(this.clientState === State.Running) {
                    this.stopLanguageClient();
                    return;
                }
                if(this.clientState === State.Stopped) {
                    this.startLanguageClient();
                }
            })
        );
        this.alertFeaturesOfConnectionTypeChange();
    }

    setConnectionType(connectionType: MinecraftConnectionType) {
        this.stopLanguageClient();

        this.connectionType.dispose();
        this.connectionType = connectionType;

        this.alertFeaturesOfConnectionTypeChange();
    }

    alertFeaturesOfConnectionTypeChange() {
        this.connectionFeatures.forEach(feature => feature.onConnectionTypeChange(this.connectionType));
    }

    startLanguageClient(): LanguageClient {
        this.prevOutputChannel?.dispose();
        const languageClient = new LanguageClient(
    		"CommandCrafter Language Client",
    		() => this.connectionType.connect().then((streamInfo) => {
                const serviceRequest = JSON.stringify({
                    "seq": 1,
                    "type": "request",
                    "command": "connectToService",
                    "arguments": "languageServer"
                });
                streamInfo.writer.write(
                    `Content-Length:${Buffer.byteLength(serviceRequest, 'utf-8')}\r\n\r\n${serviceRequest}`, 'utf-8'
                );
                return streamInfo;
            }),
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
    onConnectionTypeChange(connectionType: MinecraftConnectionType): void
}