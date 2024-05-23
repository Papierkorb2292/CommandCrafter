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
            this.channels.set(channelUpdate.name, {
                name: channelUpdate.name,
                hasCommands: channelUpdate.hasCommands,
                content: prevChannel.content.concat(channelUpdate.content)
            });
            for(const message of channelUpdate.content) {
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
        this.channels.get(message.channelName)?.content.push(message.content);
        this.consoleHandler.addMessage(message);
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