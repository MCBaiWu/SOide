package com.soide.elf;

/**
 * 单条反汇编指令
 */
public class DisassembledInstruction {

    public long address;   // 指令地址
    public byte[] bytes;   // 原始字节
    public String mnemonic; // 助记符
    public String opStr;    // 操作数

    public DisassembledInstruction(long address, byte[] bytes, String mnemonic, String opStr) {
        this.address = address;
        this.bytes = bytes;
        this.mnemonic = mnemonic;
        this.opStr = opStr;
    }

    public String getBytesHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xff));
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return String.format("0x%08x:  %-24s  %-8s %s",
                address, getBytesHex(), mnemonic, opStr != null ? opStr : "");
    }
}