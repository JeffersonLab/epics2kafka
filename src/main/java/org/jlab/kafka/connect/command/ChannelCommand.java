package org.jlab.kafka.connect.command;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelCommand other = (ChannelCommand) o;
        return Objects.equals(key, other.key) &&
                Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Key: ");
        builder.append(this.key);
        builder.append(", Value: ");
        builder.append(this.value);

        return builder.toString();
    }
}
