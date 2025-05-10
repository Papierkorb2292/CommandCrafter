import * as vscode from 'vscode';
import { LanguageClient, NotificationType, State } from "vscode-languageclient/node";
import { LanguageClientRunner } from './extension';
import { ConnectionFeature, MinecraftConnectionType } from './minecraftConnection';
import { getNonce, Message } from './webview';
import { Console, Channel, ChannelName, ConsoleMessage, ConsoleHandler, ConsoleCommnad } from './console';

interface RemoveChannelNotification {
    readonly channelName: string;
}
export class MinecraftConsole implements ConnectionFeature {

    consoleView: MinecraftConsoleViewProvider;
    console: Console;

    private client: LanguageClient | undefined;

    constructor(private readonly context: vscode.ExtensionContext, private readonly consoleViewId: string, readonly languageClientRunner: LanguageClientRunner) {
        this.consoleView = new MinecraftConsoleViewProvider(context.extensionUri, this);
        this.context.subscriptions.push(
            vscode.window.registerWebviewViewProvider(consoleViewId, this.consoleView)
        );
        this.console = new Console(this.consoleView);
    }
    onConnectionTypeChange(connectionType: MinecraftConnectionType | null) { }

    onLanguageClientReady(languageClient: LanguageClient) {
        languageClient.onNotification(new NotificationType<Channel>("createChannel"), channel => {
            this.console.addChannel(channel);
        });
        languageClient.onNotification(new NotificationType<Channel>("updateChannel"), channelUpdate => {
            this.console.updateChannel(channelUpdate);
        });
        languageClient.onNotification(new NotificationType<RemoveChannelNotification>("removeChannel"), notification => {
            this.console.removeChannel(notification.channelName);
        });
        languageClient.onNotification(new NotificationType<ConsoleMessage>("logMinecraftMessage"), message => {
            this.console.appendMessage(message);
        });

        this.client = languageClient;

        this.consoleView.onClientReady();
    }

    onLanguageClientStart(languageClient: LanguageClient) {
        this.consoleView.onClientStarting();
        this.focusConsole();
    }

    onLanguageClientStop() {
        this.consoleView.onClientStop();
    }

    focusConsole() {
        vscode.commands.executeCommand(`${this.consoleViewId}.focus`, { preserveFocus: true });
    }

    runCommand(command: ConsoleCommnad) {
        this.client?.sendNotification("runCommand", command);
    }
}

class MinecraftConsoleViewProvider implements vscode.WebviewViewProvider, ConsoleHandler {
    view: vscode.WebviewView | undefined;

    constructor(private readonly extensionUri: vscode.Uri, private readonly minecraftConsole: MinecraftConsole) { }

    resolveWebviewView(webviewView: vscode.WebviewView, context: vscode.WebviewViewResolveContext<unknown>, token: vscode.CancellationToken): void | Thenable<void> {
        webviewView.webview.options = {
			enableScripts: true,
			localResourceRoots: [
				this.extensionUri
			]
		};

        webviewView.webview.html = this._getHtmlForWebview(webviewView.webview);

        webviewView.webview.onDidReceiveMessage((data: Message) => {
            switch(data.type) {
                case "stopClient":
                    this.minecraftConsole.languageClientRunner.stopLanguageClient();
                    break;
                case "startClient":
                    this.minecraftConsole.languageClientRunner.startLanguageClient();
                    break;
                case "requestUpdate":
                    switch(this.minecraftConsole.languageClientRunner.clientState) {
                        case State.Running:
                            this.onClientReady();
                            break;
                        case State.Starting:
                            this.onClientStarting();
                            break;
                        case State.Stopped:
                            this.onClientStop();
                            break;
                    }
                    this.minecraftConsole.console.rebuildConsoleHandler();
                    break;
                case "runCommand":
                    this.minecraftConsole.runCommand(data.payload as ConsoleCommnad);
                    break;
            }
        });

        this.view = webviewView;

        this.onClientStarting();
    }

    onClientStarting() {
        this.view?.webview.postMessage({ type: "clientStarted" });
    }

    onClientReady() {
        this.view?.webview.postMessage({ type: "clientReady" });
    }
    
    onClientStop() {
        this.view?.webview.postMessage({ type: "clientStopped" });
    }

    addMessage(message: ConsoleMessage) {
        this.view?.webview.postMessage({ type: "addConsoleMessage", payload: message })
    }
    addChannel(channel: Channel) {
        this.view?.webview.postMessage({ type: "addConsoleChannel", payload: channel })
    }
    removeChannel(channelName: ChannelName) {
        this.view?.webview.postMessage({ type: "removeConsoleChannel", payload: channelName })
    }
    removeAllChannels() {
        this.view?.webview.postMessage({ type: "removeAllChannels", payload: null })
    }

    private _getHtmlForWebview(webview: vscode.Webview) {
        const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'dist', 'minecraftConsole.js'));
        const codiconsUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'node_modules', '@vscode/codicons', 'dist', 'codicon.css'));
        const nonce = getNonce();
        return /*html*/`<!DOCTYPE html>
            <html lang="en" style="height:100%">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; font-src ${webview.cspSource}; style-src vscode-resource: 'unsafe-inline';">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link href="${codiconsUri}" rel="stylesheet" id="vscode-codicon-stylesheet" nonce="${nonce}"/>
                <title>Minecraft Console</title>
            </head>
            <body>
                <script src="${scriptUri}" nonce="${nonce}"></script>
                <vscode-split-layout split="horizontal" class="ConsoleSplit" id="consoleSplit">
                    <vscode-tabs class="ChannelTabs" id="channelTabs" slot="start">
                        <vscode-button id="toggleClientButton" class="ToggleClientButton" slot="addons">Connect</vscode-button>
                    </vscode-tabs>
                    <div slot="end" class="CommandInput" id="commandInput">
                        <div class="CommandInputSpacer"></div>
                        <vscode-textarea monospace placeholder="Enter command..." id="commandInputTextarea"></vscode-textarea>
                        <div class="CommandInputSpacer"></div>
                    </div>
                </vscode-split-layout>
            </body>
            </html>`;
    }
}