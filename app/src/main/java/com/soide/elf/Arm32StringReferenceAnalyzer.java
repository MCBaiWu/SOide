package com.soide.elf;

import java.util.ArrayList;
import java.util.List;

/**
 * ARM32 字符串引用分析器（含 Thumb）。
 * <p>
 * ARM32 字符串引用典型模式：
 * <pre>
 *   LDR  R0, =str_addr      ; 字面量池 (literal pool) 在 .text 段尾
 *   ...
 *   .word 0xXXXXXXXX         ; 4 字节存放字符串地址
 *   ...
 *   LDR  R1, [R0]            ; 真正加载
 * </pre>
 * 我们追踪 LDR 指令（带 [PC, #imm] 形式或 =label 形式），从字面量池读 32-bit
 * 字符串指针，再 dereference 得到字符串内容。
 */
public class Arm32StringReferenceAnalyzer extends StringReferenceAnalyzer {

    public Arm32StringReferenceAnalyzer(SectionInfo[] sections, byte[] fileData, int machineType) {
        super(sections, fileData, machineType, Architecture.ARM32);
    }

    @Override
    protected void analyzeArch(FunctionInfo func, List<DisassembledInstruction> insns) {
        // 寄存器状态
        String[] regs = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","r9","r10","r11","r12"};

        for (int i = 0; i < insns.size(); i++) {
            DisassembledInstruction ins = insns.get(i);
            if (ins.mnemonic == null) continue;
            String m = ins.mnemonicLower();
            String op = ins.opStr == null ? "" : ins.opStr.toLowerCase();
            // Thumb: ldr rx, [pc, #imm] 形式
            // ARM:  ldr rx, =label      形式
            boolean isLdrLike = m.startsWith("ldr") && m.length() <= 6; // ldr / ldrb / ldrh
            if (!isLdrLike) continue;
            if (op.isEmpty()) continue;

            // 1) 找目标寄存器
            int comma = op.indexOf(',');
            if (comma <= 0) continue;
            String destReg = op.substring(0, comma).trim();

            // 2) 形式 A: ldr rx, =label
            int eqIdx = op.indexOf('=');
            if (eqIdx >= 0) {
                long target = extractHexValue(op.substring(eqIdx + 1));
                if (target > 0 && target >= rodataStart && target < rodataEnd) {
                    registerStringRef(ins, target, "LDR=label", func, destReg);
                    continue;
                }
            }

            // 3) 形式 B: ldr rx, [pc, #imm]  (Thumb)
            int lb = op.indexOf('[');
            int rb = op.indexOf(']');
            if (lb < 0 || rb < 0 || rb < lb) continue;
            String inside = op.substring(lb + 1, rb).trim();
            if (!inside.startsWith("pc") && !inside.startsWith("ip")) continue;
            long imm = 0;
            int hash = inside.indexOf('#');
            if (hash >= 0) imm = extractHexValue(inside.substring(hash + 1));
            // PC 值：ARM 模式 +8，Thumb 模式 +4
            long pc = func.isThumb() ? (ins.address & ~1L) + 4 : ins.address + 8;
            long poolAddr = (pc + imm) & ~3L;
            long fileOff = vaddrToFileOffset(poolAddr);
            if (fileOff < 0 || fileOff + 4 > fileData.length) continue;
            int ptr = readInt32LE(fileData, (int) fileOff);
            long signedPtr = ptr & 0xFFFFFFFFL;
            if (signedPtr < 0x80000000L) {
                // 自然对齐
            } else {
                signedPtr = ptr; // 直接用 (实际 int→long 已扩展符号)
            }
            long strAddr = signedPtr & 0xFFFFFFFFL;
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
        ref.functionName = func.name;
        ref.instruction = ins.mnemonic + " " + ins.opStr;
        ref.method = method;
        addReference(ref);
    }
}
