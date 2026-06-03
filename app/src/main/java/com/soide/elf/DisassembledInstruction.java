package com.soide.elf;

/**
 * 单条反汇编指令
 */
public class DisassembledInstruction {

    public long address;          // 指令地址
    public byte[] bytes;          // 原始字节
    public String mnemonic;       // 助记符
    public String opStr;          // 操作数

    /** 引用的字符串内容（如果这条指令是 LDR/ADR/ADRP + ADD 等加载字符串指针的指令） */
    public String referencedString;

    /** 引用字符串在文件中的虚地址 */
    public long referencedStringAddress;

    public DisassembledInstruction(long address, byte[] bytes, String mnemonic, String opStr) {
        this.address = address;
        this.bytes = bytes;
        this.mnemonic = mnemonic;
        this.opStr = opStr;
    }

    public DisassembledInstruction setStringRef(String content, long stringAddr) {
        this.referencedString = content;
        this.referencedStringAddress = stringAddr;
        return this;
    }

    public String getBytesHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xff));
        }
        return sb.toString().trim();
    }

    /** 助记符小写 */
    public String mnemonicLower() {
        return mnemonic == null ? "" : mnemonic.toLowerCase();
    }

    /** 是否为分支跳转 */
    public boolean isBranch() {
        String m = mnemonicLower();
        if (m.isEmpty()) return false;
        if (m.equals("b") || m.equals("b.eq") || m.equals("b.ne")
                || m.equals("b.gt") || m.equals("b.ge") || m.equals("b.lt") || m.equals("b.le")
                || m.equals("b.hi") || m.equals("b.ls") || m.equals("b.hs") || m.equals("b.lo")
                || m.equals("bcc") || m.equals("bcs") || m.equals("beq") || m.equals("bne")
                || m.equals("bpl") || m.equals("bmi") || m.equals("bvc") || m.equals("bvs")
                || m.equals("cbz") || m.equals("cbnz") || m.equals("tbz") || m.equals("tbnz")
                || m.equals("bl") || m.equals("blx") || m.equals("bx") || m.equals("br") || m.equals("blr")
                || m.equals("ret")) return true;
        return false;
    }

    /** 是否为函数调用 */
    public boolean isCall() {
        String m = mnemonicLower();
        return m.equals("bl") || m.equals("blr") || m.equals("blx");
    }

    /** 是否为函数返回 */
    public boolean isReturn() {
        String m = mnemonicLower();
        return m.equals("ret") || m.equals("bx") && opStr != null && opStr.contains("lr");
    }

    /** 类别标签：BRANCH / CALL / RETURN / LDR / STORE / ARITH / LOGIC / MOV / CMP / SYS / OTHER */
    public String category() {
        String m = mnemonicLower();
        if (m.isEmpty()) return "OTHER";
        if (m.equals("ret") || m.equals("bx") && opStr != null && opStr.contains("lr")) return "RETURN";
        if (m.equals("bl") || m.equals("blr") || m.equals("blx")) return "CALL";
        if (isBranch()) return "BRANCH";
        if (m.startsWith("ldr") || m.startsWith("ldur") || m.startsWith("ldp") || m.startsWith("ldnp")
                || m.startsWith("ldrb") || m.startsWith("ldrh") || m.startsWith("ldrsb") || m.startsWith("ldrsh")
                || m.startsWith("ldrsw") || m.startsWith("ldx") || m.startsWith("ldax") || m.startsWith("ldar")
                || m.equals("pop") || m.equals("adr") || m.equals("adrp") || m.equals("lea")) return "LDR";
        if (m.startsWith("str") || m.startsWith("stur") || m.startsWith("stp") || m.startsWith("stnp")
                || m.startsWith("strb") || m.startsWith("strh") || m.startsWith("stx") || m.startsWith("stlx")
                || m.equals("push")) return "STR";
        if (m.equals("mov") || m.equals("movz") || m.equals("movk") || m.equals("movn") || m.equals("movw") || m.equals("movt")
                || m.equals("mvn") || m.equals("vmov") || m.equals("xchg")) return "MOV";
        if (m.equals("add") || m.equals("adds") || m.equals("sub") || m.equals("subs")
                || m.equals("mul") || m.equals("muls") || m.equals("div") || m.equals("udiv") || m.equals("sdiv")
                || m.equals("adc") || m.equals("sbc") || m.equals("rsb") || m.equals("mla") || m.equals("mul")
                || m.equals("neg") || m.equals("negs") || m.equals("inc") || m.equals("dec")) return "ARITH";
        if (m.equals("and") || m.equals("orr") || m.equals("eor") || m.equals("bic") || m.equals("orn")
                || m.equals("lsl") || m.equals("lsr") || m.equals("asr") || m.equals("ror")
                || m.equals("xor") || m.equals("shl") || m.equals("shr") || m.equals("mvn")) return "LOGIC";
        if (m.equals("cmp") || m.equals("cmn") || m.equals("tst") || m.equals("teq") || m.equals("test")) return "CMP";
        if (m.equals("nop") || m.equals("svc") || m.equals("bkpt") || m.equals("hvc") || m.equals("smc")
                || m.equals("dmb") || m.equals("dsb") || m.equals("isb") || m.equals("mrs") || m.equals("msr")
                || m.equals("sys") || m.equals("syscall") || m.equals("int") || m.equals("cli") || m.equals("sti")
                || m.equals("hlt") || m.equals("yield") || m.equals("wfe") || m.equals("wfi") || m.equals("sev")
                || m.equals("cps") || m.equals("eret")) return "SYS";
        return "OTHER";
    }

    @Override
    public String toString() {
        return String.format("0x%08x:  %-24s  %-8s %s",
                address, getBytesHex(), mnemonic, opStr != null ? opStr : "");
    }
}
