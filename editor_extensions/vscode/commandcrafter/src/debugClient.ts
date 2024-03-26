import * as vscode from 'vscode';
import { LanguageClientRunner } from './extension';
import { ConnectionFeature, MinecraftConnectionType } from './minecraftConnection';
import { LanguageClient } from 'vscode-languageclient/node';

// The debug adapter implementation is similar to vscodes StreamDebugAdapter implementation,
// which isn't available through the API.
export class DebugClient implements ConnectionFeature {
    private static readonly TWO_CRLF = '\r\n\r\n';
	private static readonly HEADER_LINESEPARATOR = /\r?\n/;
	private static readonly HEADER_FIELDSEPARATOR = /: */;


    private connectionType: MinecraftConnectionType | undefined;

    constructor(private readonly context: vscode.ExtensionContext, debuggerId: string, readonly languageClientRunner: LanguageClientRunner) {
        const debugClient = this;
        context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory(debuggerId, {
            createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable) {
                const connectionType = debugClient.connectionType;
                if(!connectionType) {
                    throw new Error("BUG: DebugClient didn't receive connectionType");
                }

                return connectionType.connect().then((streamInfo) => {
                    const sendMessageEventEmitter = new vscode.EventEmitter<vscode.DebugProtocolMessage>()
    
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
                                            sendMessageEventEmitter.fire(<vscode.DebugProtocolMessage>JSON.parse(message));
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
                    debugAdapter.handleMessage(
                    {
                        "seq": 1,
                        "type": "request",
                        "command": "connectToService",
                        "arguments": "debugger"
                    });

                    return new vscode.DebugAdapterInlineImplementation(debugAdapter);
                }, (error) => {
                    throw new Error("Error connecting to Minecraft debugger: " + error.message);
                });
            }
        }));
    }
    
    onLanguageClientStart(languageClient: LanguageClient) { }
    onLanguageClientReady(languageClient: LanguageClient) { }
    onLanguageClientStop() { }
    onConnectionTypeChange(connectionType: MinecraftConnectionType) {
        this.connectionType?.dispose();
        this.connectionType = connectionType.copy();

    }
}