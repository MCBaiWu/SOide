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
        if (insns == null) return;
        Map<String, Long> regMap = new HashMap<>();
        Map<String, DisassembledInstruction> adrpCodeMap = new HashMap<>();

        for (int i = 0; i < insns.size(); i++) {
            DisassembledInstruction ins = insns.get(i);
            if (ins == null || ins.mnemonic == null) continue;
            String m = ins.mnemonicLower();
            String op = ins.opStr == null ? "" : ins.opStr.toLowerCase();
            try {
                if (m.equals("adrp")) {
                    String[] ops = splitByComma(op);
                    if (ops.length >= 2) {
                        String reg = ops[0].trim();
                        String immStr = ops[1].trim();
                        long pageBase = computeAdrpPageBase(ins.address, immStr);
                        if (pageBase != 0) {
                            regMap.put(reg, pageBase);
                            adrpCodeMap.put(reg, ins);
                        }
                    }
                } else if (m.equals("adr")) {
                    String[] ops = splitByComma(op);
                    if (ops.length >= 2) {
                        String reg = ops[0].trim();
                        long imm = extractHexValue(ops[1].trim());
                        imm = signExtend(imm & 0x1FFFFFL, 21);
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
                    int lb = op.indexOf('[');
                    int rb = op.indexOf(']');
                    if (lb < 0 || rb < 0 || rb <= lb) continue;
                    String inside = op.substring(lb + 1, rb).trim();
                    String[] parts = splitByComma(inside);
                    if (parts.length == 0) continue;
                    String baseReg = parts[0].trim();
                    long base = regMap.getOrDefault(baseReg, 0L);
                    long off = 0;
                    if (parts.length >= 2) off = extractHexValue(parts[1].trim());
                    long finalAddr = base + off;
                    String s = null;
                    long strAddr = 0;
                    String method = null;
                    // 1) 直接读字符串
                    long fo = vaddrToFileOffset(finalAddr);
                    if (fo >= 0 && fo < fileData.length) {
                        s = readCString(fileData, (int) fo, 256);
                        if (s != null && !s.isEmpty()) {
                            strAddr = finalAddr;
                            method = "ADRP+LDR(direct)";
                        } else if (!m.startsWith("ldrb") && !m.startsWith("ldrh")
                                && !m.startsWith("ldrsb") && !m.startsWith("ldrsh")
                                && fo + 8 <= fileData.length) {
                            // 2) 间接：8 字节指针
                            long ptr = readInt64LE(fileData, (int) fo);
                            if (ptr >= rodataStart && ptr < rodataEnd) {
                                long fo2 = vaddrToFileOffset(ptr);
                                if (fo2 >= 0 && fo2 < fileData.length) {
                                    s = readCString(fileData, (int) fo2, 256);
                                    if (s != null && !s.isEmpty()) {
                                        strAddr = ptr;
                                        method = "ADRP+LDR(indirect)";
                                    }
                                }
                            }
                        }
                    }
                    if (s != null && !s.isEmpty()) {
                        ins.setStringRef(s, strAddr);
                        registerRef(ins, strAddr, method, func);
                        DisassembledInstruction adrp = adrpCodeMap.get(baseReg);
                        if (adrp != null && adrp != ins) adrp.setStringRef(s, strAddr);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * 解析 ADRP 的目标页基址。Capstone 输出可能两种形式：
     * <ul>
     *   <li>adrp x0, 0x12345000   → 目标页地址（已 page-aligned）</li>
     *   <li>adrp x0, #0x1d, lsl #12 → 21-bit 立即数 + lsl #12</li>
     *   <li>adrp x0, #imm          → 21-bit 立即数（无 lsl）</li>
     * </ul>
     */
    private long computeAdrpPageBase(long insAddr, String immStr) {
        // 形式 1: 0x... (已经是页基址或被解析为绝对页)
        int hexIdx = immStr.indexOf("0x");
        if (hexIdx >= 0) {
            long v = extractHexValue(immStr);
            if (v != 0) {
                // 判断是否是 21-bit 立即数（值 < 2^21）
                if (v < 0x200000L) {
                    long imm = signExtend(v, 21);
                    return (insAddr & ~0xFFFL) + (imm << 12);
                }
                // 否则认为已经是页基址
                return v & ~0xFFFL;
            }
        }
        // 形式 2: #imm, lsl #12
        if (immStr.contains("lsl") && immStr.contains("12")) {
            int hash = immStr.indexOf('#');
            long imm = extractHexValue(hash >= 0 ? immStr.substring(hash + 1) : immStr);
            imm = signExtend(imm & 0x1FFFFFL, 21);
            return (insAddr & ~0xFFFL) + (imm << 12);
        }
        return 0;
    }

    private void tryResolveString(DisassembledInstruction ins, long addr, String method,
                                  Map<String, Long> regMap,
                                  Map<String, DisassembledInstruction> adrpCodeMap,
                                  FunctionInfo func) {
        if (addr <= 0) return;
        long fo = vaddrToFileOffset(addr);
        if (fo < 0 || fo >= fileData.length) return;
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
