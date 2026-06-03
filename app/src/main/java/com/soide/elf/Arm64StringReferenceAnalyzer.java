package com.soide.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ARM64 (AArch64) 字符串引用分析器。
 * <p>
 * AArch64 字符串引用典型模式：
 * <pre>
 *   ADRP  X0, page          ; 加载页基址 (4KB 对齐)
 *   ADD   X0, X0, #lo12:str ; 加上低 12-bit 偏移
 *   LDR   X1, [X0]          ; 读 8 字节指针
 *   ...
 *   .ascii "Hello, world!\0"
 * </pre>
 * 关键：跟踪每个寄存器当前值（page-base 来自 ADRP，full-address 来自 ADD），
 *     然后解 LDR 找字符串。
 */
public class Arm64StringReferenceAnalyzer extends StringReferenceAnalyzer {

    public Arm64StringReferenceAnalyzer(SectionInfo[] sections, byte[] fileData, int machineType) {
        super(sections, fileData, machineType, Architecture.ARM64);
    }

    @Override
    protected void analyzeArch(FunctionInfo func, List<DisassembledInstruction> insns) {
        Map<String, Long> regMap = new HashMap<>();
        Map<String, DisassembledInstruction> adrpCodeMap = new HashMap<>();

        for (int i = 0; i < insns.size(); i++) {
            DisassembledInstruction ins = insns.get(i);
            if (ins.mnemonic == null) continue;
            String m = ins.mnemonicLower();
            String op = ins.opStr == null ? "" : ins.opStr.toLowerCase();
            try {
                if (m.equals("adrp")) {
                    String[] ops = splitByComma(op);
                    if (ops.length >= 2) {
                        String reg = ops[0].trim();
                        long imm = signExtend(extractHexValue(ops[1].trim()) & 0x1FFFFFL, 21);
                        long pageBase = (ins.address & ~0xFFFL) + (imm << 12);
                        regMap.put(reg, pageBase);
                        adrpCodeMap.put(reg, ins);
                    }
                } else if (m.equals("adr")) {
                    String[] ops = splitByComma(op);
                    if (ops.length >= 2) {
                        String reg = ops[0].trim();
                        long imm = signExtend(extractHexValue(ops[1].trim()) & 0x1FFFFFL, 21);
                        long addr = (ins.address & ~3L) + imm;
                        regMap.put(reg, addr);
                        tryResolveString(ins, addr, "ADR", regMap, adrpCodeMap, func);
                    }
                } else if (m.equals("add") || m.equals("adds")) {
                    String[] ops = splitByComma(op);
                    if (ops.length >= 3) {
                        String destReg = ops[0].trim();
                        String srcReg = ops[1].trim();
                        if (regMap.containsKey(srcReg)) {
                            long base = regMap.get(srcReg);
                            long off = extractHexValue(ops[2].trim());
                            if (ops[2].contains("lsl") && ops[2].contains("12")) off <<= 12;
                            long finalAddr = base + off;
                            regMap.put(destReg, finalAddr);
                            tryResolveString(ins, finalAddr, "ADRP+ADD", regMap, adrpCodeMap, func);
                        }
                    }
                } else if (m.equals("ldr") || m.equals("ldur") || m.equals("ldrb") || m.equals("ldrh")
                        || m.equals("ldrsb") || m.equals("ldrsh") || m.equals("ldrsw") || m.equals("ldp")) {
                    // ldr x0, [x1] 或 [x1, #off]
                    int lb = op.indexOf('[');
                    int rb = op.indexOf(']');
                    if (lb < 0 || rb < 0) continue;
                    String inside = op.substring(lb + 1, rb).trim();
                    String[] parts = splitByComma(inside);
                    if (parts.length == 0) continue;
                    String baseReg = parts[0].trim();
                    long base = regMap.getOrDefault(baseReg, 0L);
                    long off = 0;
                    if (parts.length >= 2) off = extractHexValue(parts[1].trim());
                    long finalAddr = base + off;
                    // 直接是字符串（ASCII at this address）
                    String s = readCString(fileData, (int) vaddrToFileOffset(finalAddr), 256);
                    if (s != null && !s.isEmpty()) {
                        ins.setStringRef(s, finalAddr);
                        registerRef(ins, finalAddr, "ADRP+LDR(direct)", func);
                        DisassembledInstruction adrp = adrpCodeMap.get(baseReg);
                        if (adrp != null && adrp != ins) adrp.setStringRef(s, finalAddr);
                    } else {
                        // 8/4 字节指针 → dereference
                        long fo = vaddrToFileOffset(finalAddr);
                        if (fo < 0) continue;
                        long ptr = m.startsWith("ldrb") || m.startsWith("ldrh") ? 0
                                : (m.startsWith("ldp") ? readInt64LE(fileData, (int) fo)
                                : readInt64LE(fileData, (int) fo));
                        if (ptr > 0 && ptr >= rodataStart && ptr < rodataEnd) {
                            s = readCString(fileData, (int) vaddrToFileOffset(ptr), 256);
                            if (s != null && !s.isEmpty()) {
                                ins.setStringRef(s, ptr);
                                registerRef(ins, ptr, "ADRP+LDR(indirect)", func);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore one bad insn
            }
        }
    }

    private void tryResolveString(DisassembledInstruction ins, long addr, String method,
                                  Map<String, Long> regMap,
                                  Map<String, DisassembledInstruction> adrpCodeMap,
                                  FunctionInfo func) {
        if (addr <= 0) return;
        long fo = vaddrToFileOffset(addr);
        if (fo < 0) return;
        String s = readCString(fileData, (int) fo, 256);
        if (s == null || s.isEmpty()) return;
        ins.setStringRef(s, addr);
        registerRef(ins, addr, method, func);
    }

    private void registerRef(DisassembledInstruction ins, long strAddr, String method, FunctionInfo func) {
        StringReference ref = new StringReference();
        ref.insnAddress = ins.address;
        ref.stringAddress = strAddr;
        ref.stringContent = ins.referencedString;
        ref.functionName = func == null ? "" : func.name;
        ref.instruction = ins.mnemonic + " " + ins.opStr;
        ref.method = method;
        addReference(ref);
    }

    private String[] splitByComma(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int d = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '{') d++;
            else if (c == ']' || c == ')' || c == '}') d--;
            else if (c == ',' && d == 0) {
                out.add(cur.toString().trim());
                cur = new StringBuilder();
            } else cur.append(c);
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
}
