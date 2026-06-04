package com.soide.elf;

import java.util.List;

/**
 * ARM32 字符串引用分析器（含 Thumb）。
 * <p>
 * ARM32 字符串引用典型模式：
 * <pre>
 *   LDR  R0, =str_addr      ; 字面量池 (literal pool) 在 .text 段尾
 *   ...
 *   .word 0xXXXXXXXX         ; 4 字节存放字符串虚地址
 *   ...
 *   LDR  R1, [R0]            ; 真正加载
 * </pre>
 * <p>
 * 我们追踪：
 * <ul>
 *   <li>LDR Rx, =label  / LDR Rx, =0xADDR  → 直接 target</li>
 *   <li>LDR Rx, [PC, #imm] / LDR Rx, [ip, #imm]  (Thumb) → 从字面量池读 32-bit 字符串指针</li>
 *   <li>LDR Rx, [PC], #imm  (ARM) → 同上</li>
 * </ul>
 */
public class Arm32StringReferenceAnalyzer extends StringReferenceAnalyzer {

    public Arm32StringReferenceAnalyzer(SectionInfo[] sections, byte[] fileData, int machineType) {
        super(sections, fileData, machineType, Architecture.ARM32);
    }

    @Override
    protected void analyzeArch(FunctionInfo func, List<DisassembledInstruction> insns) {
        if (insns == null) return;
        boolean isThumb = func != null && func.isThumb;

        for (int i = 0; i < insns.size(); i++) {
            DisassembledInstruction ins = insns.get(i);
            if (ins == null || ins.mnemonic == null) continue;
            String m = ins.mnemonicLower();
            String op = ins.opStr == null ? "" : ins.opStr.toLowerCase().trim();
            if (op.isEmpty()) continue;

            // 限定 LDR 加载字 / 加载字到寄存器的情形（取字符串指针）
            // 排除 str/ldm/push/pop 之类
            if (!(m.equals("ldr") || m.equals("ldr.w") || m.equals("ldrd") ||
                  m.equals("ldrh") || m.equals("ldrb"))) {
                continue;
            }

            // 1) 找目标寄存器
            int comma = op.indexOf(',');
            if (comma <= 0) continue;
            String destReg = op.substring(0, comma).trim();

            // 2) 形式 A: ldr rx, =label / =0xADDR
            int eqIdx = op.indexOf('=');
            if (eqIdx > 0) {
                long target = extractHexValue(op.substring(eqIdx + 1));
                if (target > 0 && target >= rodataStart && target < rodataEnd) {
                    registerStringRef(ins, target, "LDR=label", func, destReg);
                    continue;
                }
            }

            // 3) 形式 B: ldr rx, [pc, #imm] / [pc], #imm / [ip, #imm]
            int lb = op.indexOf('[');
            int rb = op.indexOf(']');
            if (lb < 0 || rb < 0 || rb <= lb) continue;
            String inside = op.substring(lb + 1, rb).trim();
            // inside 应以 "pc" 或 "ip" 开头
            if (!(inside.startsWith("pc") || inside.startsWith("ip"))) continue;

            long imm = 0;
            int hash = inside.indexOf('#');
            if (hash >= 0) {
                imm = extractHexValue(inside.substring(hash + 1));
            } else {
                // 可能是 "pc, 0x20" 或 "pc, 20"
                int pcComma = inside.indexOf(',');
                if (pcComma >= 0) {
                    imm = extractHexValue(inside.substring(pcComma + 1).trim());
                }
            }

            // PC 值：ARM 模式 +8，Thumb 模式 +4
            long pc = isThumb ? ((ins.address & ~1L) + 4) : (ins.address + 8);
            long poolAddr = (pc + imm) & ~3L;
            long fileOff = vaddrToFileOffset(poolAddr);
            if (fileOff < 0 || fileOff + 4 > fileData.length) continue;
            int ptr = readInt32LE(fileData, (int) fileOff);
            long strAddr = ptr & 0xFFFFFFFFL;
            if (strAddr < rodataStart || strAddr >= rodataEnd) continue;
            registerStringRef(ins, strAddr, "LDR[PC,#" + imm + "]", func, destReg);
        }
    }

    private void registerStringRef(DisassembledInstruction ins, long strAddr, String method,
                                   FunctionInfo func, String destReg) {
        long fo = vaddrToFileOffset(strAddr);
        if (fo < 0) return;
        String content = readCString(fileData, (int) fo, 256);
        if (content == null || content.isEmpty()) return;

        ins.setStringRef(content, strAddr);
        StringReference ref = new StringReference();
        ref.insnAddress = ins.address;
        ref.stringAddress = strAddr;
        ref.stringContent = content;
        ref.functionName = func != null ? func.name : "";
        ref.instruction = ins.mnemonic + " " + ins.opStr;
        ref.method = method;
        addReference(ref);
    }
}
