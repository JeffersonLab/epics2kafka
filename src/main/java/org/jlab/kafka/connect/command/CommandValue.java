package org.jlab.kafka.connect.command;

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
}
