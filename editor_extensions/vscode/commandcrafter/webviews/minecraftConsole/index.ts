import './index.scss';
import { Channel, ChannelName, ConsoleMessage } from '../../src/console';
import { on } from 'events';

declare var acquireVsCodeApi: any;
const vscode = acquireVsCodeApi();

interface Message {
    type: string,
    payload: any
}

interface ChannelElements {
    channelContents: HTMLElement
    channelSelectorOption: HTMLOptionElement
}

addEventListener("load", () => {
    vscode.postMessage({ type: "requestUpdate" });

    const toggleClientButton = document.getElementById("toggleClientButton")!;
    toggleClientButton.addEventListener("click", toggleClientRunning);

    const channelElements = new Map<ChannelName, ChannelElements>();
    const channelSelector = document.getElementById("channelSelector")! as HTMLSelectElement;
    const log = document.getElementById("log")!;

    addEventListener("message", (ev: MessageEvent<Message>) => {
        switch(ev.data.type) {
            case "clientReady":
                toggleClientButton.textContent = "Stop language client";
                toggleClientButton.classList.value = "clientStopButton";
                break;
            case "clientStopped":
                toggleClientButton.textContent = "Start language client";
                toggleClientButton.classList.value = "clientStartButton";

                channelSelector.innerHTML = "";
                channelElements.clear();
                log.innerHTML = "";
                break;
            case "clientStarted":
                toggleClientButton.textContent = "Starting...";
                toggleClientButton.classList.value = "clientStartButton";
                break;
            case "addConsoleChannel":
                const channel = ev.data.payload as Channel;
                const channelElement = document.createElement("div");

                const channelOption = document.createElement("option");
                channelOption.value = channel.name;
                channelOption.textContent = channel.name;
                channelSelector.appendChild(channelOption);

                channelElements.set(channel.name, { channelContents: channelElement, channelSelectorOption: channelOption });

                if(channelElements.size === 1) {
                    log.appendChild(channelElement);
                }

                for(const message of channel.content) {
                    addConsoleMessage(channelElement, message);
                }

                break;
            case "removeConsoleChannel":
                const channelName = ev.data.payload as ChannelName;
                const channelElementToRemove = channelElements.get(channelName);
                if(!channelElementToRemove) break;
                channelElements.delete(channelName);
                channelSelector.removeChild(channelElementToRemove.channelSelectorOption);

                if(channelElements.size === 0) {
                    log.removeChild(channelElementToRemove.channelContents);
                    break;
                }

                log.replaceChild(channelElements.values().next().value.channelContents, log.firstElementChild!);
                break;
            case "removeAllChannels":
                channelElements.clear();
                log.innerHTML = "";
                channelSelector.innerHTML = "";
                break;
            case "addConsoleMessage":
                const message = ev.data.payload as ConsoleMessage;
                const targetChannel = channelElements.get(message.channelName);
                if(!targetChannel) break;

                addConsoleMessage(targetChannel.channelContents, message.content);
                break;
        }
    });

    channelSelector.addEventListener("change", onChannelSelectorChange);
    let commandInput = document.getElementById("commandInput");
    commandInput?.addEventListener("keypress", onCommandInputKeyDown);

    function onChannelSelectorChange(this: HTMLElement) {
        const channelName = (this as HTMLSelectElement).value;
        const channelElement = channelElements.get(channelName);
        if(channelElement) {
            log.replaceChild(channelElement.channelContents, log.firstElementChild!);
        }
    }

    function toggleClientRunning(this: HTMLElement) {
        let shouldStart = this.textContent === "Start language client";
        vscode.postMessage({ "type": shouldStart ? "startClient" : "stopClient" });
    }

    function onCommandInputKeyDown(this: HTMLElement, event: KeyboardEvent) {
        if(event.key !== "Enter") {
            return true;
        }
        if(event.shiftKey) {
            return true;
        }
        event.preventDefault();
        if("value" in this) {
            vscode.postMessage({ type: "runCommand", payload: { channel: channelSelector.value, command: this.value }});
            this.value = "";
        }
    }

    function addConsoleMessage(targetChannel: HTMLElement, content: String) {
        const prefixEnd = content.indexOf("]") + 1
        const prefixElement = document.createElement("span");
        const logLevelEndChar = content.charAt(prefixEnd - 2);
        prefixElement.classList.add(logLevelEndChar === "R" ? "logLevelError" : logLevelEndChar === "N" ? "logLevelWarn" : "logLevelInfo");
        prefixElement.textContent = content.substring(0, prefixEnd);
        targetChannel.append(prefixElement);

        let messageStart = prefixEnd;

        if(content.charAt(prefixEnd + 1) === "(") {
            const sourceEnd = content.indexOf(")", prefixEnd) + 1;
            const sourceElement = document.createElement("span");
            sourceElement.classList.add("source");
            sourceElement.textContent = content.substring(prefixEnd, sourceEnd);
            targetChannel.append(sourceElement);

            messageStart = sourceEnd;
        }

        const messageContentElement = document.createElement("span");
        messageContentElement.textContent = content.substring(messageStart);
        if(logLevelEndChar === "R") {
            messageContentElement.classList.add("errorMessageContent");
        }
        if(logLevelEndChar === "N") {
            messageContentElement.classList.add("warnMessageContent");
        }
        targetChannel.append(messageContentElement);
        targetChannel.append(document.createElement("br"));
    }
});