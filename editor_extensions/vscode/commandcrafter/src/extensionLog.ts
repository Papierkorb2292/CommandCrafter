import * as vscode from 'vscode'

export let outputChannel: vscode.OutputChannel | null = null

export function activateLog(context: vscode.ExtensionContext) {
    outputChannel = vscode.window.createOutputChannel("CommandCrafter")
    context.subscriptions.push({
        dispose() {
            outputChannel?.dispose();
            outputChannel = null;
        }
    });
}