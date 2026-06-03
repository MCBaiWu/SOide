package com.soide.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字符串引用分析器基类。
 * <p>
 * 目标：对于一段函数内的反汇编指令，识别哪些指令是 "加载字符串常量地址" 的指令，
 * 并把目标字符串内容关联回指令，方便 UI 高亮。
 * <p>
 * 两种主流 ABI：
 * <ul>
 *   <li>ARM32 (含 Thumb)：LDR Rx, =str  +  字面量池 (literal pool)</li>
 *   <li>ARM64 (AArch64)：ADRP Xn, page  +  ADD Xn, Xn, #:lo12:str  或 LDR/STR</li>
 * </ul>
 * 实现参考 fenghuo.elf StringReferenceAnalyzer 的 ARM32/ARM64 子类。
 */
public abstract class StringReferenceAnalyzer {

    public enum Architecture { ARM32, ARM64, X86, X86_64, UNKNOWN }

    public static class SectionInfo {
        public final String name;
        public final long addr;
        public final long size;
        public SectionInfo(String name, long addr, long size) {
            this.name = name;
            this.addr = addr;
            this.size = size;
        }
    }

    public static class StringReference {
        public long insnAddress;          // 引用指令地址
        public long stringAddress;       // 字符串虚地址
        public String stringContent;     // 字符串内容
        public String functionName;      // 所在函数
        public String instruction;       // 完整汇编
        public String method;            // "LDR+literal" / "ADRP+ADD" / "ADRP+LDR" / "ADR"

        public String referenceType() { return method; }
    }

    protected final SectionInfo[] sections;
    protected final byte[] fileData;
    protected final int machineType;
    protected final Architecture arch;
    protected long rodataStart = 0, rodataEnd = 0;

    /** 由指令地址索引：哪些指令被关联了字符串 */
    protected final Map<Long, StringReference> refsByInsnAddr = new HashMap<>();
    /** 由字符串虚地址索引：哪些指令引用了它 */
    protected final Map<Long, List<StringReference>> refsByStringAddr = new HashMap<>();

    protected StringReferenceAnalyzer(SectionInfo[] sections, byte[] fileData, int machineType, Architecture arch) {
        this.sections = sections == null ? new SectionInfo[0] : sections;
        this.fileData = fileData;
        this.machineType = machineType;
        this.arch = arch;
        // 找 .rodata / .data 范围
        for (SectionInfo s : this.sections) {
            if (s.name == null) continue;
            if (s.name.contains(".rodata") || s.name.contains(".data.rel.ro")) {
                if (s.addr < rodataStart || rodataStart == 0) rodataStart = s.addr;
                long end = s.addr + s.size;
                if (end > rodataEnd) rodataEnd = end;
            }
        }
        if (rodataStart == 0) {
            for (SectionInfo s : this.sections) {
                if (s.name != null && s.name.contains(".rodata")) {
                    if (s.addr < rodataStart || rodataStart == 0) rodataStart = s.addr;
                    long end = s.addr + s.size;
                    if (end > rodataEnd) rodataEnd = end;
                }
            }
        }
    }

    public Architecture getArchitecture() { return arch; }
    public String getArchitectureName() { return arch.name(); }

    public Map<Long, StringReference> refsByInsnAddr() { return refsByInsnAddr; }
    public Map<Long, List<StringReference>> refsByStringAddr() { return refsByStringAddr; }

    /** 在函数反汇编结果上做字符串引用分析，把结果回填到 instruction.referencedString */
    public void analyze(FunctionInfo func, List<DisassembledInstruction> insns) {
        if (func == null || insns == null || insns.isEmpty()) return;
        // 先清空
        refsByInsnAddr.clear();
        refsByStringAddr.clear();
        // 调用架构特定分析
        analyzeArch(func, insns);
    }

    protected abstract void analyzeArch(FunctionInfo func, List<DisassembledInstruction> insns);

    // ==================== 公共工具方法 ====================

    protected long vaddrToFileOffset(long vaddr) {
        if (sections == null) return -1;
        for (SectionInfo s : sections) {
            if (vaddr >= s.addr && vaddr < s.addr + s.size) {
                return vaddr - s.addr; // 简化：假设 fileOff = vaddr - section.addr
                // 实际 fileOff 应该是 fileOffInSection + s.fileOff,
                // 但这里 SectionInfo 内部用 addr==fileOff 的简化模型
            }
        }
        return -1;
    }

    protected String readCString(byte[] data, int fileOff, int maxLen) {
        if (data == null || fileOff < 0 || fileOff >= data.length) return null;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        int o = fileOff;
        while (o < data.length && n < maxLen) {
            byte b = data[o++];
            if (b == 0) break;
            if (b >= 0x20 && b <= 0x7e) {
                sb.append((char) b);
            } else {
                // 非可打印字符也允许（短串），但开头不能是非可打印
                if (n == 0) return null;
                break;
            }
            n++;
        }
        String s = sb.toString();
        return s.length() >= 1 ? s : null;
    }

    protected int readInt32LE(byte[] data, int fileOff) {
        if (data == null || fileOff < 0 || fileOff + 4 > data.length) return 0;
        return (data[fileOff] & 0xff)
                | ((data[fileOff + 1] & 0xff) << 8)
                | ((data[fileOff + 2] & 0xff) << 16)
                | ((data[fileOff + 3] & 0xff) << 24);
    }

    protected long readInt64LE(byte[] data, int fileOff) {
        if (data == null || fileOff < 0 || fileOff + 8 > data.length) return 0;
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= ((long) (data[fileOff + i] & 0xff)) << (i * 8);
        }
        return v;
    }

    protected long signExtend(long v, int bits) {
        long mask = 1L << (bits - 1);
        if ((v & mask) != 0) v |= -1L << bits;
        return v;
    }

    /** 从字符串中提取十六进制值（兼容 0x 前缀、# 前缀、:lo12:） */
    protected long extractHexValue(String s) {
        if (s == null) return 0;
        int loIdx = s.indexOf(":lo12:");
        if (loIdx >= 0) s = s.substring(loIdx + 6);
        int idx = s.indexOf("0x");
        if (idx < 0) idx = s.indexOf("0X");
        if (idx < 0) {
            // 纯数字（十进制）也兼容
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-') digits.append(c);
                else if (digits.length() > 0) break;
            }
            if (digits.length() == 0) return 0;
            try { return Long.parseLong(digits.toString()); } catch (Exception e) { return 0; }
        }
        boolean negative = idx > 0 && s.charAt(idx - 1) == '-';
        int start = idx + 2;
        int end = start;
        while (end < s.length()) {
            char c = s.charAt(end);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) end++;
            else break;
        }
        if (end > start) {
            try {
                long v = Long.parseUnsignedLong(s.substring(start, end), 16);
                return negative ? -v : v;
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    protected String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    protected void addReference(StringReference ref) {
        if (ref == null) return;
        refsByInsnAddr.put(ref.insnAddress, ref);
        List<StringReference> list = refsByStringAddr.get(ref.stringAddress);
        if (list == null) {
            list = new ArrayList<>();
            refsByStringAddr.put(ref.stringAddress, list);
        }
        list.add(ref);
    }
}
