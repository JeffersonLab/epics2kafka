package org.jlab.kafka.connect.command;

import java.util.Objects;

public class CommandValue {
    private String mask;
    private String outkey;

    public CommandValue() {

    }

    public CommandValue(String mask, String outkey) {
        this.mask = mask;
        this.outkey = outkey;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getOutkey() {
        return outkey;
    }

    public void setOutkey(String outkey) {
        this.outkey = outkey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandValue other = (CommandValue) o;
        return Objects.equals(mask, other.mask) &&
                Objects.equals(outkey, other.outkey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mask, outkey);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Mask: ");
        builder.append(this.mask);
        builder.append(", Outkey: ");
        builder.append(this.outkey);

        return builder.toString();
    }
}
