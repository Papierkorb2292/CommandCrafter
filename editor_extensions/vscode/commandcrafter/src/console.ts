export const MAXIMUM_CONSOLE_MESSAGES = 256 // Arbitrary number, but should match the limit that the mod uses

export type ChannelName = string;

export interface Channel {
    readonly name: string;
    readonly hasCommands: boolean;
    readonly content: string[];
}

export interface ConsoleMessage {
    readonly channelName: string;
    readonly content: string;
}

export class Console {
    channels: Map<ChannelName, Channel> = new Map();
    
    constructor(private readonly consoleHandler: ConsoleHandler) { }

    addChannel(channel: Channel) {
        this.channels.set(channel.name, channel);
        this.consoleHandler.addChannel(channel);
    }

    updateChannel(channelUpdate: Channel) {
        const prevChannel = this.channels.get(channelUpdate.name);
        if (prevChannel) {
            const channelContent = prevChannel.content.slice()
            const newMessagesStartIndex = appendMessagesWithMaximumSize(channelContent, ...channelUpdate.content)
            this.channels.set(channelUpdate.name, {
                name: channelUpdate.name,
                hasCommands: channelUpdate.hasCommands,
                content: channelContent
            });
            for(const message of channelContent.slice(newMessagesStartIndex)) {
                this.consoleHandler.addMessage({
                    channelName: channelUpdate.name,
                    content: message
                });
            }
        }
    }

    removeChannel(channelName: ChannelName) {
        this.channels.delete(channelName);
        this.consoleHandler.removeChannel(channelName);
    }

    appendMessage(message: ConsoleMessage) {
        const channelContent = this.channels.get(message.channelName)?.content;
        if(channelContent) {
            appendMessagesWithMaximumSize(channelContent, message.content)
            this.consoleHandler.addMessage(message);
        }
    }

    rebuildConsoleHandler() {
        this.consoleHandler.removeAllChannels();
        for(const channel of this.channels.values()) {
            this.consoleHandler.addChannel(channel);
        }
    }
}

export interface ConsoleHandler {
    addChannel(channel: Channel): void;
    removeChannel(channelName: ChannelName): void;
    removeAllChannels(): void;
    addMessage(message: ConsoleMessage): void;
}

export interface ConsoleCommnad {
    readonly channel: string;
    readonly command: string;
}

/**
 * Adds all given messages to the end of the list, and removes elements from the start of the list if
 * the size would otherwise be greater than MAXIMUM_CONSOLE_MESSAGES
 * @param list The destination to add the messages to
 * @param messages The messages to add
 * @returns The index in the list at which the appended messages start
 */
export function appendMessagesWithMaximumSize(list: string[], ...messages: string[]): number {
    if(messages.length > MAXIMUM_CONSOLE_MESSAGES) {
        messages = messages.slice(0, MAXIMUM_CONSOLE_MESSAGES)
    }
    const removedMessagesCount = Math.max(0, list.length + messages.length - MAXIMUM_CONSOLE_MESSAGES)
    list.splice(0, removedMessagesCount)
    const startIndex = list.length
    list.push(...messages)
    return startIndex
}