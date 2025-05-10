import "@vscode-elements/elements/dist/bundled.js";
import './index.scss';
import AnsiToHtmlConverter = require("ansi-to-html");
import { Channel, ChannelName, ConsoleMessage } from '../../src/console';

declare var acquireVsCodeApi: any;
const vscode = acquireVsCodeApi();

interface Message {
    type: string,
    payload: any
}

interface ChannelData {
    name: ChannelName,
    channelHeader: HTMLElement
    channelLogContainer: HTMLElement
    channelLogContent: ScrollableElement
    pendingMessages: string[]
}

type TabsElement = HTMLElement & { selectedIndex: number }
type SplitElement = HTMLElement & { handlePosition: string }
type TextfieldElement = HTMLElement & { wrappedElement: HTMLElement & { value: string } }
type ScrollableElement = HTMLElement & { scrollMax: number, scrollPos: number }
type IconElement = HTMLElement & { name: string }

const channelNamesByIndex: ChannelName[] = [ ]
const channelElements = new Map<ChannelName, ChannelData>();

function buildAnsiConverter() {
    return new AnsiToHtmlConverter({
        colors: {
            6: "var(--vscode-editorInfo-foreground)", //INFO
            3: "var(--vscode-editorWarning-foreground)", //WARN
            1: "var(--vscode-debugIcon-stopForeground)", //ERROR
        },
        stream: true
    });
}

function setInitialConsoleSplitPosition(consoleSplit: SplitElement) {
    const height = consoleSplit.offsetHeight;
    // Make the command input a bit more than one line tall to make it clear that it accepts multiline inputs
    const commandInputGoalHeight = 3 * parseFloat(window.getComputedStyle(consoleSplit).fontSize);
    consoleSplit.handlePosition = (height - commandInputGoalHeight).toString();
}

function toggleClientRunning(this: HTMLElement) {
    let shouldStart = this.textContent === "Connect";
    vscode.postMessage({ "type": shouldStart ? "startClient" : "stopClient" });
}

function setupToggleClientButton(toggleClientButton: HTMLElement) {
    toggleClientButton.addEventListener("click", toggleClientRunning);
}

function setupCommandInput(commandInput: TextfieldElement, channelTabs: TabsElement) {
    commandInput.wrappedElement.addEventListener("keypress", event => {
        if(event.key !== "Enter") {
            return true;
        }
        if(event.shiftKey) {
            return true;
        }
        event.preventDefault();
        vscode.postMessage({ type: "runCommand", payload: { channel: channelNamesByIndex[channelTabs.selectedIndex], command: commandInput.wrappedElement.value }});
        commandInput.wrappedElement.value = "";
    });
}

function convertAnsiLines(lines: string[], converter: AnsiToHtmlConverter): HTMLElement {
    const container = document.createElement("div")
    console.log(lines)
    container.innerHTML = converter.toHtml(lines.join("\n"))
    console.log(container.innerHTML)
    return container
}

function addConsoleMessage(targetChannel: ChannelData, channelTabs: TabsElement, content: string) {
    // Only add messages when the channel is focused so it's easy to determine whether the channel should scroll to the bottom
    if(channelNamesByIndex[channelTabs.selectedIndex] !== targetChannel.name) {
        if(targetChannel.pendingMessages.length > 255)
            targetChannel.pendingMessages.splice(0, targetChannel.pendingMessages.length - 255);
        targetChannel.pendingMessages.push(content);
        return;
    }

    const lines = content.split('\n');

    const title = lines.slice(0, 2);
    const detail = lines.slice(2);

    const ansiConverter = buildAnsiConverter();

    const logContent = targetChannel.channelLogContent
    const isScrolledToBottom = logContent.scrollPos + logContent.offsetHeight + 1 >= logContent.scrollMax;

    const messageContentElement = convertAnsiLines(title, ansiConverter)
    if(detail.length == 0) {
        messageContentElement.classList.add("NonExpandableLogEntry")
        logContent.append(messageContentElement)
    } else {
        const collapsibleContainer = document.createElement("div")
        const collapsibleContainerTitle = document.createElement("div")
        collapsibleContainerTitle.classList.add("ExpandableLogEntryTitle")

        const arrow = document.createElement("vscode-icon") as IconElement
        arrow.name = "chevron-right"
        arrow.classList.add("ExpandableLogEntryArrow")

        const detailContentElement = convertAnsiLines(detail, ansiConverter);
        detailContentElement.classList.add("ExpandableLogEntryDetail")
        detailContentElement.style.height = "0";

        // Toggle collapsible
        arrow.addEventListener("click", () => {
            const isCollapsed = arrow.name == "chevron-right"
            if(isCollapsed) {
                arrow.name = "chevron-down"
                detailContentElement.style.height = "unset";
            } else {
                arrow.name = "chevron-right"
                detailContentElement.style.height = "0";
            }
        });

        collapsibleContainerTitle.append(arrow);
        collapsibleContainerTitle.append(messageContentElement);
        collapsibleContainer.append(collapsibleContainerTitle);
        collapsibleContainer.append(detailContentElement);
        logContent.append(collapsibleContainer);
    }

    if(isScrolledToBottom) {
        logContent.scrollPos = logContent.scrollMax - logContent.offsetHeight;
    }

    while(logContent.children.length > 256) {
        logContent.removeChild(logContent.firstChild!);
    }
}

function scheduleAddConsoleMessages(channelData: ChannelData, channelTabs: TabsElement, messagesProvider: () => string[]) {
    const resizeObserver = new ResizeObserver(() => {
        resizeObserver.disconnect()
        for(const message of messagesProvider()) {
            addConsoleMessage(channelData, channelTabs, message);
        }
    })
    resizeObserver.observe(channelData.channelLogContent);
}

function setupMessageListeners(toggleClientButton: HTMLElement, channelTabs: TabsElement) {
    addEventListener("message", (ev: MessageEvent<Message>) => {
        switch(ev.data.type) {
            case "clientReady":
                toggleClientButton.textContent = "Disconnect";
                break;
            case "clientStopped":
                toggleClientButton.textContent = "Connect";
                channelNamesByIndex.length = 0;
                break;
            case "clientStarted":
                toggleClientButton.textContent = "Connecting...";
                channelElements.forEach(channel => {
                    channelTabs.removeChild(channel.channelHeader);
                    channelTabs.removeChild(channel.channelLogContainer);
                })
                channelElements.clear();
                break;
            case "addConsoleChannel":
                const channel = ev.data.payload as Channel;

                const channelHeader = document.createElement("vscode-tab-header");
                channelHeader.slot = "header";
                channelHeader.classList.add("ChannelHeader");
                channelHeader.textContent = channel.name;

                const channelLogContainer = document.createElement("vscode-tab-panel");
                channelLogContainer.classList.add("Log");

                const channelLogContent = document.createElement("vscode-scrollable") as ScrollableElement;
                channelLogContainer.appendChild(channelLogContent);
                     
                channelTabs.append(channelHeader);
                channelTabs.append(channelLogContainer);

                const channelData = { name: channel.name, channelHeader, channelLogContainer, channelLogContent, pendingMessages: [] }
                channelElements.set(channel.name, channelData);
                channelNamesByIndex.push(channel.name);

                // Wait for new elements to setup before adding logs, so scroll works correctly
                scheduleAddConsoleMessages(channelData, channelTabs, () => channel.content)
                break;
            case "removeConsoleChannel":
                const channelName = ev.data.payload as ChannelName;
                const channelElementToRemove = channelElements.get(channelName);
                if(!channelElementToRemove) break;
                channelElements.delete(channelName);
                channelNamesByIndex.splice(channelNamesByIndex.indexOf(channelName), 1)
                channelTabs.removeChild(channelElementToRemove.channelHeader);
                channelTabs.removeChild(channelElementToRemove.channelLogContainer);
                break;
            case "removeAllChannels":
                channelElements.forEach(channel => {
                    channelTabs.removeChild(channel.channelHeader);
                    channelTabs.removeChild(channel.channelLogContainer);
                })
                channelElements.clear();
                channelNamesByIndex.length = 0;
                break;
            case "addConsoleMessage":
                const message = ev.data.payload as ConsoleMessage;
                const targetChannel = channelElements.get(message.channelName);
                if(!targetChannel) break;

                addConsoleMessage(targetChannel, channelTabs, message.content);
                break;
        }
    });
}

function setupPendingMessagesHandler(channelTabs: TabsElement) {
    channelTabs.addEventListener("vsc-tabs-select", () => {
        const channelData = channelElements.get(channelNamesByIndex[channelTabs.selectedIndex])!;
        // Wait for subsequent resize, so scroll is meaningful
        scheduleAddConsoleMessages(channelData, channelTabs, () => channelData.pendingMessages.splice(0))
    });
}

addEventListener("load", () => {
    vscode.postMessage({ type: "requestUpdate" });

    const toggleClientButton = document.getElementById("toggleClientButton")!;
    const split = document.getElementById("consoleSplit") as SplitElement;
    const channelTabs = document.getElementById("channelTabs") as TabsElement;
    const commandInputContainer = document.getElementById("commandInputTextarea") as TextfieldElement;

    setInitialConsoleSplitPosition(split);
    setupToggleClientButton(toggleClientButton);
    setupMessageListeners(toggleClientButton, channelTabs);
    setupCommandInput(commandInputContainer, channelTabs);
    setupPendingMessagesHandler(channelTabs)
})