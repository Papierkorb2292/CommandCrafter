import * as vscode from 'vscode';
import { LanguageClientRunner, findFiles } from './extension';
import { ConnectionFeature, MinecraftConnectionType } from './minecraftConnection';
import { LanguageClient, StreamInfo } from 'vscode-languageclient/node';
import { MinecraftConsole } from './minecraftConsole';

// The debug adapter implementation is similar to vscodes StreamDebugAdapter implementation,
// which isn't available through the API.
export class DebugClient implements ConnectionFeature {
    private static readonly TWO_CRLF = '\r\n\r\n';
	private static readonly HEADER_LINESEPARATOR = /\r?\n/;
	private static readonly HEADER_FIELDSEPARATOR = /: */;

    private connectionType: MinecraftConnectionType | undefined;

    private languageClientRunning = false;

    constructor(private readonly context: vscode.ExtensionContext, debuggerId: string, readonly minecraftConsole: MinecraftConsole, readonly languageClientRunner: LanguageClientRunner) {
        const debugClient = this;
        context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory(debuggerId, {
            createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable) {
                const connectionType = debugClient.connectionType;
                if(!connectionType) {
                    throw new Error("BUG: DebugClient didn't receive connectionType");
                }

                return connectionType.connect().then((streamInfo) => {
                    return debugClient.connectToStream(streamInfo, connectionType);
                }, (error) => {
                    throw new Error("Error connecting to Minecraft debugger: " + error.message);
                });
            }
        }));
    }

    private connectToStream(streamInfo: StreamInfo, connectionType: MinecraftConnectionType): vscode.DebugAdapterDescriptor {
        const sendMessageEventEmitter = new vscode.EventEmitter<vscode.DebugProtocolMessage>()

        const debugAdapter: vscode.DebugAdapter = {
            onDidSendMessage: sendMessageEventEmitter.event,
            handleMessage(message) {
                const json = JSON.stringify(message);
                streamInfo.writer.write(`Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n${json}`, 'utf8');
            },
            dispose() {
                connectionType.dispose();
            }
        }

        let rawData = Buffer.allocUnsafe(0);
        let contentLength = -1;
        streamInfo.reader.on('data', (data) => {
            rawData = Buffer.concat([rawData, data]);

            while(true) {
                if(contentLength >= 0) {
                    if(rawData.length >= contentLength) {
                        const message = rawData.toString('utf8', 0, contentLength);
                        rawData = rawData.slice(contentLength);
                        contentLength = -1;
                        if(message.length > 0) {
                            try {
                                this.handleReceivedMessage(
                                    JSON.parse(message),
                                    sendMessageEventEmitter,
                                    debugAdapter.handleMessage
                                );
                            } catch (e) {
                                console.error(e);
                            }
                        }
                        continue;
                    }
                } else {
                    const idx = rawData.indexOf(DebugClient.TWO_CRLF);
                    if (idx !== -1) {
                        const header = rawData.toString('utf8', 0, idx);
                        const lines = header.split(DebugClient.HEADER_LINESEPARATOR);
                        for (const h of lines) {
                            const kvPair = h.split(DebugClient.HEADER_FIELDSEPARATOR);
                            if (kvPair[0] === 'Content-Length') {
                                contentLength = Number(kvPair[1]);
                            }
                        }
                        rawData = rawData.slice(idx + DebugClient.TWO_CRLF.length);
                        continue;
                    }
                }
                break;
            }
        });
        
        debugAdapter.handleMessage(
        {
            "seq": 1,
            "type": "request",
            "command": "connectToService",
            "arguments": "debugger"
        });
        return new vscode.DebugAdapterInlineImplementation(debugAdapter);
    }

    private handleReceivedMessage(message: any, vscodeHandleEventEmitter: vscode.EventEmitter<vscode.DebugProtocolMessage>, messageSender: (message: any) => void) {
        if(message.type === 'request' && message.command == 'findFiles') {
            findFiles(message.arguments as string).then(files => {
                messageSender({
                        type: 'response',
                        request_seq: message.seq,
                        success: true,
                        body: files
                    });
            }, error => {
                messageSender({
                    type: 'response',
                    request_seq: message.seq,
                    success: false,
                    message: 'cancelled',
                    body: { error: error.message }
                });
            });
            return;
        }
        if(message.type === 'request' && message.command == 'getWorkspaceRoot') {
            const workspaceFolders = vscode.workspace.workspaceFolders
            messageSender({
                type: 'response',
                request_seq: message.seq,
                success: true,
                body: workspaceFolders == null || workspaceFolders.length != 1 ? null : workspaceFolders[0].uri.toString()
            });
            return;
        }

        vscodeHandleEventEmitter.fire(<vscode.DebugProtocolMessage>message);

        if(message.type === "response" && message.command == "configurationDone") {
            // Focus the minecraft console since vscode focused the debug console
            if(this.languageClientRunning) {
                this.minecraftConsole.focusConsole();
            }
        }
    }
    
    onLanguageClientStart(languageClient: LanguageClient) {
        this.languageClientRunning = true;
    }
    onLanguageClientReady(languageClient: LanguageClient) { }
    onLanguageClientStop() {
        this.languageClientRunning = false;
    }
    onConnectionTypeChange(connectionType: MinecraftConnectionType | null) {
        this.connectionType?.dispose();
        this.connectionType = connectionType?.copy();

    }
}