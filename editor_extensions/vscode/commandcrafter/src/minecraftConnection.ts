import * as vscode from 'vscode';
import {
    Disposable,
	LanguageClient,
	State,
	StreamInfo
} from 'vscode-languageclient/node';
import * as net from 'net';
import { MinecraftConsole } from './minecraftConsole';
import { LanguageClientRunner, checkUpdateMinecraftAddress, fileExists, findFiles } from './extension';
import { DebugClient } from './debugClient';
import { ScoreboardStorageViewer } from './scoreboardStorageViewer';
import { outputChannel } from './extensionLog';
import { ExtensionCompatibility } from './extensionCompatibility';
import { FeatureConfig, getFeatureConfig, insertDefaultFeatureConfig } from './settings';

export interface MinecraftConnectionType extends Disposable {

    connect(): Promise<StreamInfo>;
    copy(): MinecraftConnectionType;
}

export class AddressConfigMalformedError {
    public message = "Unable to parse Minecraft address config: Must be in the format 'hostname:port'"
}

export function parseAddressConfig(addressConfig: string): MinecraftConnectionType | AddressConfigMalformedError {
    const segments = addressConfig.split(':')
    if(segments.length !== 2)
        return new AddressConfigMalformedError()
    const port = Number(segments[1])
    if(Number.isNaN(port))
        return new AddressConfigMalformedError()
    return new SocketConnectionType(segments[0], port)
}

export class SocketConnectionType implements MinecraftConnectionType {

    connection: net.Socket | null = null;

    constructor(public address: string, public port: number) { }

    connect(): Promise<StreamInfo> {
        outputChannel?.appendLine(`Connecting to Minecraft on ${this.address}:${this.port}`)
        return new Promise((resolve, reject) => {
            const connection = this.connection = net.connect({ host: this.address, port: this.port });
            connection.on('connect', () => resolve({
                writer: connection,
                reader: connection
            }));
            connection.on('error', reject);
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
    /**
     * The version of the CommandCrafter mod. If this is null, there is either no connection or
     * the mod is too old and doesn't send its version yet (before 0.6.0)
     */
    connectedModVersion: string | null = null;

    connectionFeatures: ConnectionFeature[] = [ ];

    readonly extensionVersion: string;

    constructor(private connectionType: MinecraftConnectionType | null, context: vscode.ExtensionContext) {
        const minecraftConsole = new MinecraftConsole(context, "commandcrafter.console", this);
        this.connectionFeatures.push(minecraftConsole);
        this.connectionFeatures.push(new ScoreboardStorageViewer(context, "commandcrafter.scoreboard_storage_viewer", "scoreboardStorage"))
        this.connectionFeatures.push(new DebugClient(context, "commandcrafter", minecraftConsole, this))
        this.connectionFeatures.push(new ExtensionCompatibility());
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
        this.extensionVersion = context.extension.packageJSON.version;
    }

    setConnectionType(connectionType: MinecraftConnectionType) {
        this.stopLanguageClient();

        this.connectionType?.dispose();
        this.connectionType = connectionType;

        this.alertFeaturesOfConnectionTypeChange();
    }

    alertFeaturesOfConnectionTypeChange() {
        this.connectionFeatures.forEach(feature => feature.onConnectionTypeChange(this.connectionType));
    }

    startLanguageClient() {
        checkUpdateMinecraftAddress()

        this.prevOutputChannel?.dispose();
        this.connectionType?.connect().then((streamInfo) => {
            outputChannel?.appendLine(`Connecting to Minecraft Language server with feature config: ${JSON.stringify(getFeatureConfig())}`)
            const serviceRequest = JSON.stringify({
                "seq": 1,
                "type": "request",
                "command": "connectToService",
                "arguments": {
                    "service": "languageServer",
                    "featureConfig": getFeatureConfig(),
                    "extensionVersion": this.extensionVersion
                }
            });
            streamInfo.writer.write(
                `Content-Length:${Buffer.byteLength(serviceRequest, 'utf-8')}\r\n\r\n${serviceRequest}`, 'utf-8'
            );
            const languageClient = new LanguageClient(
                "commandCrafterLanguageClient",
                "CommandCrafter Language Client",
                () => Promise.resolve(streamInfo),
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
                        outputChannel?.appendLine("LanguageClient is starting")
                        this.connectionFeatures.forEach(feature => feature.onLanguageClientStart(languageClient));
                        break;
                    case State.Running:
                        outputChannel?.appendLine("LanguageClient is running")
                        this.connectionFeatures.forEach(feature => feature.onLanguageClientReady(languageClient));
                        languageClient.onRequest("findFiles", (filePattern: string) => findFiles(filePattern))
                        languageClient.onRequest("fileExists", (filePattern: string) => fileExists(filePattern))
                        languageClient.onRequest("getFileContent", (path: string) =>
                            vscode.workspace.fs.readFile(vscode.Uri.parse(path)).then(buffer => buffer.toString()))
                        languageClient.sendRequest<FeatureConfig>("defaultFeatureConfig").then(defaultConfig =>
                            insertDefaultFeatureConfig(defaultConfig))
                        languageClient.onNotification("modVersion", (version: string) => this.connectedModVersion = version)
                        break;
                    case State.Stopped:
                        outputChannel?.appendLine("LanguageClient is stopping")
                        languageClient.diagnostics?.clear()
                        this.connectionFeatures.forEach(feature => feature.onLanguageClientStop());
                        break;
                }
            });
    
            const timeoutPromise = new Promise<void>(resolve => setTimeout(() => resolve(), 10000))
            return Promise.race([languageClient.start().then(() => {
                this.languageClient = languageClient;
            }), timeoutPromise.then(() => {
                throw new TimeoutError("Connection to Minecraft Language Server timed out");
            })]);
        }).catch((error) => {
            vscode.window.showInformationMessage(`Can't connect to Minecraft Language Server: ${error}`);
            outputChannel?.appendLine(`Can't connect to Minecraft Language Server: ${error.stack}`)
            this.connectionType?.dispose()
            this.languageClient = null;
            this.connectedModVersion = null;
        })
    }

    stopLanguageClient(): void {
        let languageClient = this.languageClient;
        this.languageClient = null;
        this.connectedModVersion = null;
        languageClient?.stop();
    }

    dispose() {
        this.connectionType?.dispose();
    }
}

export interface ConnectionFeature {
    onLanguageClientStart(languageClient: LanguageClient): void;
    onLanguageClientReady(languageClient: LanguageClient): void;
    onLanguageClientStop(): void;
    onConnectionTypeChange(connectionType: MinecraftConnectionType | null): void
}

class TimeoutError extends Error {
    readonly name = "TimeoutError"
    constructor(message: string) {
        super(message);
    }
}