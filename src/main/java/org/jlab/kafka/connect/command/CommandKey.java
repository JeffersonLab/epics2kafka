package org.jlab.kafka.connect.command;

import java.util.Objects;

public class CommandKey {
    private String topic; // Output topic
    private String channel; // CA channel to monitor

    public CommandKey() {

    }

    public CommandKey(String topic, String channel) {
        this.topic = topic;
        this.channel = channel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandKey key = (CommandKey) o;
        return Objects.equals(topic, key.topic) &&
                Objects.equals(channel, key.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, channel);
    }
}
