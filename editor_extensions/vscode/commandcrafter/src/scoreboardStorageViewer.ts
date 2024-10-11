import * as vscode from 'vscode'
import { ConnectionFeature, MinecraftConnectionType } from './minecraftConnection';
import { FileChangeType, LanguageClient } from 'vscode-languageclient/node';
import { assert } from 'console';

function hasNbtEditor() {
    let foundNbtEditor = false
    for(const extension of vscode.extensions.all) {
        const customEditors = extension.packageJSON.contributes?.customEditors
        if(customEditors === undefined)
            continue
        for(const customEditor of customEditors) {
            const selectors = customEditor.selector
            if(selectors === undefined)
                continue
            for(const selector of selectors) {
                if(selector.filenamePattern === "*.nbt") {
                    foundNbtEditor = true
                }
            }
        }
    }
    return foundNbtEditor
}

export class ScoreboardStorageViewer implements ConnectionFeature {

    private viewProvider: ScoreboardStorageTreeDataProvider | null = null;
    private viewProviderDisposable: vscode.Disposable | null = null;
    private documentProvider: vscode.FileSystemProvider | null = null;
    private documentProviderDisposable: vscode.Disposable | null = null;
    readonly foundNbtEditor: boolean;
    languageClient: LanguageClient | null = null

    scoreboards: string[] = []
    storages: string[] = []

    constructor(private readonly context: vscode.ExtensionContext, private readonly scoreboardStorageViewerId: string, readonly scoreboardStorageFileSystemScheme: string) {
        this.foundNbtEditor = hasNbtEditor()
    }

    onLanguageClientStart(languageClient: LanguageClient): void { }
    onLanguageClientReady(languageClient: LanguageClient): void {
        this.languageClient = languageClient
        this.documentProvider = new ScoreboardStorageFileSystemProvider(this)
        const contentProviderDisposable = vscode.workspace.registerFileSystemProvider(
            this.scoreboardStorageFileSystemScheme,
            this.documentProvider
        )
        this.documentProviderDisposable = contentProviderDisposable
        this.context.subscriptions.push(contentProviderDisposable)
        this.viewProvider = new ScoreboardStorageTreeDataProvider(this)
        const viewDisposable = vscode.window.registerTreeDataProvider(
            this.scoreboardStorageViewerId,
            this.viewProvider
        )
        this.viewProviderDisposable = viewDisposable
        this.context.subscriptions.push(viewDisposable)
    }
    onLanguageClientStop(): void {
        this.viewProvider?.dispose()
        this.viewProviderDisposable?.dispose()
        this.documentProviderDisposable?.dispose()
        this.documentProviderDisposable = null
        this.viewProviderDisposable = null
        this.languageClient = null
    }

    onConnectionTypeChange(connectionType: MinecraftConnectionType | null): void { }
}

class ScoreboardStorageTreeDataProvider implements vscode.TreeDataProvider<ScoreboardStorageTreeItem>, vscode.Disposable {
    
    private readonly fileSystemWatcher: vscode.FileSystemWatcher
    
    constructor(private readonly scoreboardStorageViewer: ScoreboardStorageViewer) {
        this.fileSystemWatcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(
                vscode.Uri.parse(`${scoreboardStorageViewer.scoreboardStorageFileSystemScheme}:///`),
                "**"
            )
        )
        this.fileSystemWatcher.onDidChange(uri => {
            this.refresh()
        })
    }
    
    private onDidChangeTreeDataEmitter: vscode.EventEmitter<ScoreboardStorageTreeItem | undefined> = new vscode.EventEmitter();

    readonly onDidChangeTreeData: vscode.Event<ScoreboardStorageTreeItem | undefined> = this.onDidChangeTreeDataEmitter.event;

    getTreeItem(element: ScoreboardStorageTreeItem): vscode.TreeItem | Thenable<vscode.TreeItem> {
        //TODO: Add icons to entries
        if(element.type === "scoreboard") {
            const treeItem = new vscode.TreeItem(element.scoreboardName)
            treeItem.contextValue = "scoreboard"
            treeItem.command = {
                command: "vscode.open",
                title: "Open Scoreboard",
                arguments: [
                    vscode.Uri.parse(`${this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme}:///scoreboards/${element.scoreboardName}.json`)
                ]
            }
            return treeItem
        }
        assert(element.type === "storage")
        const treeItem = new vscode.TreeItem(element.storageName)
        treeItem.contextValue = "storage"
        treeItem.command = {
            command: "vscode.open",
            title: "Open Storage",
            arguments: [
                vscode.Uri.parse(`${this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme}:///storages/${element.storageName}.${this.scoreboardStorageViewer.foundNbtEditor ? "nbt" : "snbt"}`)
            ]
        }
        return treeItem
    }
    getChildren(element?: ScoreboardStorageTreeItem | undefined): vscode.ProviderResult<ScoreboardStorageTreeItem[]> {
        if(element !== undefined)
            return []

        return Promise.all([
            vscode.workspace.fs.readDirectory(vscode.Uri.parse(`${this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme}:///scoreboards/`)),
            vscode.workspace.fs.readDirectory(vscode.Uri.parse(`${this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme}:///storages/`))
        ]).then(([scoreboards, storages]) => {
            const scoreboardItems: ScoreboardTreeItem[] = scoreboards.map(([name]) => ({
                type: "scoreboard",
                scoreboardName: name.slice(
                    this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme.length + ':///scoreboards/'.length,
                    -5
                )
            }))
            let storageItems: StorageTreeItem[]
            if(this.scoreboardStorageViewer.foundNbtEditor) {
                storageItems = storages
                    .filter(([name]) => name.endsWith(".nbt"))
                    .map(([name]) => ({ type: "storage", storageName: name.slice(
                        this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme.length + ':///storages/'.length,
                        -4
                    )
                }))
            } else {
                storageItems = storages
                    .filter(([name]) => name.endsWith(".snbt"))
                    .map(([name]) => ({ type: "storage", storageName: name.slice(
                        this.scoreboardStorageViewer.scoreboardStorageFileSystemScheme.length + ':///storages/'.length,
                        -5
                    )
                }))
            }
            return [...scoreboardItems, ...storageItems]
        })
    }

    refresh(): void {
        this.onDidChangeTreeDataEmitter.fire(undefined);
    }

    dispose() {
        this.fileSystemWatcher.dispose()
    }
}

type ScoreboardStorageTreeItem = ScoreboardTreeItem | StorageTreeItem

interface ScoreboardTreeItem {
    type: "scoreboard",
    scoreboardName: string
}

interface StorageTreeItem {
    type: "storage",
    storageName: string
}

/**
 * This file system contains the scoreboards and storages of the connected Minecraft instance.
 * 
 * The scoreboards can be accessed through the path `/scoreboard/<scoreboardName>.json`
 * and the storages can be accessed through the path `/storage/<storageName>.snbt` or `/storage/<storageName>.nbt`.
 */
class ScoreboardStorageFileSystemProvider implements vscode.FileSystemProvider {
    private readonly onDidChangeFileEmitter: vscode.EventEmitter<vscode.FileChangeEvent[]> = new vscode.EventEmitter();
    onDidChangeFile = this.onDidChangeFileEmitter.event;

    private nextWatcherId = 0;

    constructor(private readonly scoreboardStorageViewer: ScoreboardStorageViewer) {
        scoreboardStorageViewer.languageClient?.onNotification("scoreboardStorageFileSystem/onDidChangeFile", (args: { events: { uri: string, type: FileChangeType }[]}) => {
            this.onDidChangeFileEmitter.fire(args.events.map(event => ({ type: event.type, uri: vscode.Uri.parse(event.uri)})))
        })
    }
    
    watch(uri: vscode.Uri, options: { readonly recursive: boolean; readonly excludes: readonly string[]; }): vscode.Disposable {
        const watcherId = this.nextWatcherId++
        this.makeNotification("scoreboardStorageFileSystem/watch", { uri: uri.toString(), ...options, watcherId }, uri)
        return new vscode.Disposable(() => {
            this.makeNotification("scoreboardStorageFileSystem/removeWatch", { watcherId }, uri)
        })
    }
    stat(uri: vscode.Uri): vscode.FileStat | Thenable<vscode.FileStat> {
        return this.makeRequest("scoreboardStorageFileSystem/stat", { uri: uri.toString() }, uri)
    }
    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] | Thenable<[string, vscode.FileType][]> {
        return this.makeRequest("scoreboardStorageFileSystem/readDirectory", { uri: uri.toString() }, uri)
    }
    createDirectory(uri: vscode.Uri): void | Thenable<void> {
        return this.makeRequest("scoreboardStorageFileSystem/createDirectory", { uri: uri.toString() }, uri)
    }
    readFile(uri: vscode.Uri): Uint8Array | Thenable<Uint8Array> {
        return this.makeRequest<{ contentBase64: string}>("scoreboardStorageFileSystem/readFile", { uri: uri.toString() }, uri)
            .then(({ contentBase64 }) => Buffer.from(contentBase64, "base64"))
    }
    writeFile(uri: vscode.Uri, content: Uint8Array, options: { readonly create: boolean; readonly overwrite: boolean; }): void | Thenable<void> {
        return this.makeRequest("scoreboardStorageFileSystem/writeFile", { uri: uri.toString(), contentBase64: Buffer.from(content).toString("base64"), ...options }, uri)
    }
    delete(uri: vscode.Uri, options: { readonly recursive: boolean; }): void | Thenable<void> {
        return this.makeRequest("scoreboardStorageFileSystem/delete", { uri: uri.toString(), ...options }, uri)
    }
    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { readonly overwrite: boolean; }): void | Thenable<void> {
        return this.makeRequest("scoreboardStorageFileSystem/rename", { uldUri: oldUri.toString(), newUri: newUri.toString(), ...options }, oldUri)
    }

    private makeRequest<T>(request: string, args: any, errorMsg: vscode.Uri | string): Promise<T> {
        var promise = this.scoreboardStorageViewer.languageClient
            ?.sendRequest<T | { fileNotFoundErrorMessage: string }>(request, args)
        if(!promise)
            throw vscode.FileSystemError.FileNotFound(errorMsg)
        return promise.catch(_ => {
            throw vscode.FileSystemError.FileNotFound(errorMsg)
        }).then(result => {
            if(result && typeof result === 'object' && 'fileNotFoundErrorMessage' in result) {
                throw vscode.FileSystemError.FileNotFound(result.fileNotFoundErrorMessage)
            }
            return result
        })
    }

    private makeNotification(notification: string, args: any, errorMsg: vscode.Uri | string): Promise<void> {
        var promise = this.scoreboardStorageViewer.languageClient
            ?.sendNotification(notification, args)
        if(!promise)
            throw vscode.FileSystemError.FileNotFound(errorMsg)
        return promise.catch(_ => {
            throw vscode.FileSystemError.FileNotFound(errorMsg)
        })
    }
}