package com.soide.elf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 线性扫描 + 序言模式匹配：在可执行节区上识别函数入口。
 * 参考 C++ 实现 ARM64_PROLOGUE_PATTERNS / THUMB_PUSH 模式。
 */
public class LinearSweepAnalyzer {

    public static final int SOURCE_SYMTAB = 0;
    public static final int SOURCE_LINEARSWEEP = 1;

    // ARM64 典型函数序言 4 字节模式 (stp x29, x30, [sp, #imm]! + mov x29, sp + paciasp 等)
    private static final byte[][] ARM64_PROLOGUES = {
            {(byte) 0xFD, (byte) 0x7B, (byte) 0xBF, (byte) 0xA9}, // stp x29, x30, [sp, #-X]!
            {(byte) 0xFD, (byte) 0x7B, (byte) 0x01, (byte) 0xA9}, // stp x29, x30, [sp, #imm]
            {(byte) 0xF3, (byte) 0x53, (byte) 0xBF, (byte) 0xA9}, // stp x19, x30, [sp, #-X]!
            {(byte) 0xFF, (byte) 0xC3, (byte) 0x00, (byte) 0xD1}, // sub sp, sp, #0x...
            {(byte) 0xFD, (byte) 0x7B, (byte) 0xBF, (byte) 0xB9}, // ldp 还原 frame
            {(byte) 0xFD, (byte) 0x7B, (byte) 0x01, (byte) 0xB9},
            {(byte) 0xBF, (byte) 0x23, (byte) 0x03, (byte) 0xD5}, // paciasp
            {(byte) 0x9F, (byte) 0x24, (byte) 0x03, (byte) 0xD5}, // pacibsp
            {(byte) 0xBF, (byte) 0x24, (byte) 0x03, (byte) 0xD5}, // autiasp
    };

    // ARM32 Thumb push {reg_list, lr} 范围 0xB500-0xB5FF
    // (push {r7, lr} = 0xB5F0, push {r4, lr} = 0xB510, ...)
    private static final int THUMB_PUSH_MIN = 0xB500;
    private static final int THUMB_PUSH_MAX = 0xB5FF;

    // Thumb 常见 prologue 前缀: push + mov r7, sp / add r7, sp, #imm
    private static final int[] THUMB_MOV_R7_SP = {0x466F, 0x44AF}; // mov r7, sp / add r7, sp, #imm

    /**
     * 在单个节区上扫描函数入口。
     */
    public static List<Long> scan(SectionHeader sh, byte[] data, int machine, boolean is64) {
        List<Long> hits = new ArrayList<>();
        if (sh == null || sh.shOffset + sh.shSize > data.length) return hits;

        int off0 = (int) sh.shOffset;
        int sz = (int) sh.shSize;

        if (machine == ElfConstants.EM_AARCH64) {
            for (int off = 0; off + 4 <= sz; off += 4) {
                long addr = sh.shAddr + off;
                if ((addr & 3) != 0) continue;
                if (matchesAny(data, off0 + off, ARM64_PROLOGUES)) {
                    hits.add(addr);
                }
            }
        } else if (machine == ElfConstants.EM_ARM) {
            for (int off = 0; off + 4 <= sz; off += 2) {
                long addr = sh.shAddr + off;
                if (off0 + off + 2 > data.length) break;
                int hw = (data[off0 + off] & 0xff) | ((data[off0 + off + 1] & 0xff) << 8);
                if (hw >= THUMB_PUSH_MIN && hw <= THUMB_PUSH_MAX) {
                    // 进一步看后面是不是 mov r7, sp (强化识别)
                    if (off + 4 <= sz && off0 + off + 4 <= data.length) {
                        int hw2 = (data[off0 + off + 2] & 0xff) | ((data[off0 + off + 3] & 0xff) << 8);
                        if (hw2 == THUMB_MOV_R7_SP[0] || hw2 == THUMB_MOV_R7_SP[1]) {
                            hits.add(addr);
                            continue;
                        }
                    }
                    // 没有 mov r7, sp 但有 push {reg, lr} 也算
                    // 强制 LR 在 push 列表里: PUSH 指令编码 bit 8 set
                    if ((hw & 0x100) != 0) {
                        hits.add(addr);
                    }
                }
            }
        } else if (machine == ElfConstants.EM_386 || machine == ElfConstants.EM_X86_64) {
            // x86 函数序言: 0x55 (push ebp/rbp) 在可执行节区出现
            for (int off = 0; off + 1 < sz; off++) {
                if (data[off0 + off] == (byte) 0x55) {
                    long addr = sh.shAddr + off;
                    hits.add(addr);
                }
            }
        }
        return hits;
    }

    private static boolean matchesAny(byte[] data, int off, byte[][] patterns) {
        if (off + 4 > data.length) return false;
        for (byte[] pat : patterns) {
            if (data[off] == pat[0] && data[off + 1] == pat[1]
                    && data[off + 2] == pat[2] && data[off + 3] == pat[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并符号表函数和扫描找到的函数。
     * 优先用符号表里的（带名字），再补充扫描到的。
     */
    public static List<FunctionInfo> mergeWithSymbols(List<FunctionInfo> symFuncs,
                                                      List<Long> scanned,
                                                      SectionHeader sh,
                                                      Disassembler disasm,
                                                      int machine,
                                                      byte[] data) {
        Set<Long> known = new HashSet<>();
        for (FunctionInfo f : symFuncs) known.add(f.address);

        List<FunctionInfo> out = new ArrayList<>(symFuncs);
        if (sh == null || sh.shSize < 4) return out;
        long fileOff = sh.shOffset;
        long fileEnd = sh.shOffset + sh.shSize;
        if (fileEnd > data.length) fileEnd = data.length;

        for (Long addr : scanned) {
            if (known.contains(addr)) continue;
            long size = estimateSize(addr, sh, scanned, machine);
            long end = Math.min(addr + size, sh.shAddr + sh.shSize);
            long readOff = fileOff + (addr - sh.shAddr);
            if (readOff < fileOff) continue;
            int readLen = (int) Math.min(size, fileEnd - readOff);
            if (readLen <= 0) continue;

            byte[] code = Arrays.copyOfRange(data, (int) readOff, (int) (readOff + readLen));

            boolean thumb = (machine == ElfConstants.EM_ARM && (addr & 1L) == 1L);
            disasm.setThumb(thumb);
            List<DisassembledInstruction> insns = disasm.disassemble(code, addr & ~1L);

            FunctionInfo fi = new FunctionInfo(
                    "sub_" + Long.toHexString(addr & ~1L),
                    addr & ~1L, size, sh.name != null ? sh.name : "");
            fi.instructions = insns;
            fi.isThumb = thumb;
            fi.source = SOURCE_LINEARSWEEP;
            out.add(fi);
            known.add(addr);
        }
        return out;
    }

    /**
     * 估算函数大小：到下一个扫描到的函数为止；最多 4KB。
     */
    private static long estimateSize(long addr, SectionHeader sh, List<Long> scanned, int machine) {
        long minDelta = 4 * 1024L;
        for (Long a : scanned) {
            if (a <= addr) continue;
            long d = a - addr;
            if (d < minDelta) minDelta = d;
        }
        if (minDelta > 4096) minDelta = 4096;
        return minDelta;
    }
}
