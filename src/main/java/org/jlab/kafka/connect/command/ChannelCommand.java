package org.jlab.kafka.connect.command;

public class ChannelCommand {
    public static final ChannelCommand KEEP_ALIVE = new ChannelCommand(null, null);

    private CommandKey key;
    private CommandValue value;

    public ChannelCommand() {

    }

    public ChannelCommand(CommandKey key, CommandValue value) {
        this.key = key;
        this.value = value;
    }

    public CommandKey getKey() {
        return key;
    }

    public CommandValue getValue() {
        return value;
    }
}
