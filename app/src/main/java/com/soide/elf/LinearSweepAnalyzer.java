package com.soide.elf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 线性扫描 (Linear Sweep) 函数边界探测器。
 *
 * 通过识别常见架构的函数序言 (prologue) 字节特征来发现 .symtab/.dynsym
 * 之外的"隐式"函数入口。识别是启发式的，结果可能包含误报。
 */
public class LinearSweepAnalyzer {

    public static final int SOURCE_SYMTAB = 0;
    public static final int SOURCE_LINEARSWEEP = 1;

    private LinearSweepAnalyzer() {}

    /**
     * 对一个可执行节区进行线性扫描，返回候选函数入口地址列表 (相对节区起点的偏移)。
     */
    public static List<Long> scan(SectionHeader sec, byte[] data, int elfMachine, boolean is64Bit) {
        List<Long> hits = new ArrayList<>();
        if (sec == null || sec.shSize < 4) return hits;
        int len = (int) Math.min(sec.shSize, data.length - (int) sec.shOffset);
        if (len <= 0) return hits;
        int start = (int) sec.shOffset;
        int end = start + len;

        switch (elfMachine) {
            case ElfConstants.EM_ARM:
                scanArm(sec, data, start, end, hits);
                break;
            case ElfConstants.EM_AARCH64:
                scanAarch64(sec, data, start, end, hits);
                break;
            case ElfConstants.EM_386:
                scanX86(sec, data, start, end, false, hits);
                break;
            case ElfConstants.EM_X86_64:
                scanX86(sec, data, start, end, true, hits);
                break;
        }
        return hits;
    }

    /**
     * ARM 模式：识别 push {...lr} / push {r.., lr} 模式 (A1 encoding: 0xe92d????)
     * Thumb 模式：识别 PUSH {...} (0xb5??)
     * 经验做法是直接扫描所有 4 字节 ARM prologue 与 2 字节 Thumb prologue。
     */
    private static void scanArm(SectionHeader sec, byte[] data, int start, int end, List<Long> hits) {
        // 1) ARM 4 字节 prologue 0xe92d???? (push)
        for (int i = start; i + 4 <= end; i += 4) {
            if ((data[i] & 0xff) == 0x2d && (data[i + 1] & 0xff) == 0xe9) {
                hits.add((long) (i - start));
            }
        }
        // 2) Thumb 2 字节 push 0xb5?? (low 8-bit: 0xb500 ~ 0xb5ff)
        for (int i = start; i + 2 <= end; i += 2) {
            if ((data[i] & 0xff) == 0xb5) {
                hits.add((long) (i - start));
            }
        }
    }

    /**
     * AArch64 模式：识别 stp x29, x30, [sp, #imm]! (0xa9bf7bfd/0xa9bc7bfd/...)
     * 这通常是函数序言。
     */
    private static void scanAarch64(SectionHeader sec, byte[] data, int start, int end, List<Long> hits) {
        // little-endian: bytes 0..3 contain the 32-bit instruction
        for (int i = start; i + 4 <= end; i += 4) {
            int insn = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);
            // stp x29, x30, [sp, #imm]!
            // 1010 1001 1??1 1101 1111 1000 1011 1101 (简化匹配: 高位 0xa9b?7bfd)
            if ((insn & 0xffe003e0) == 0xa9a07be0) {
                hits.add((long) (i - start));
            }
        }
    }

    /**
     * x86/x64 模式：识别常见函数序言 0x55 (push rbp) 0x48 0x89 0xe5 (mov rbp, rsp)
     */
    private static void scanX86(SectionHeader sec, byte[] data, int start, int end, boolean is64, List<Long> hits) {
        for (int i = start; i + 4 < end; i++) {
            if ((data[i] & 0xff) == 0x55) {
                if (is64) {
                    // 48 89 e5 = mov rbp, rsp
                    if (i + 4 < end && (data[i + 1] & 0xff) == 0x48
                            && (data[i + 2] & 0xff) == 0x89
                            && (data[i + 3] & 0xff) == 0xe5) {
                        hits.add((long) (i - start));
                    }
                } else {
                    // 89 e5 = mov ebp, esp
                    if (i + 3 < end && (data[i + 1] & 0xff) == 0x89
                            && (data[i + 2] & 0xff) == 0xe5) {
                        hits.add((long) (i - start));
                    }
                }
            }
        }
    }

    /**
     * 给定若干扫描结果（相对节区起点的偏移），过滤掉与已知 symtab 函数入口地址 (相对节区起点) 重复的项。
     * 返回合并后的 (offset, source) 列表，offset 单位为节区起点。
     */
    public static List<FunctionInfo> mergeWithSymbols(List<FunctionInfo> symtabFuncs,
                                                       List<Long> sweepHits,
                                                       SectionHeader sec,
                                                       Disassembler disasm,
                                                       int machineType,
                                                       byte[] data) {
        Set<String> symKeys = new TreeSet<>();
        for (FunctionInfo f : symtabFuncs) {
            if (sec.shAddr > 0 && f.address >= sec.shAddr) {
                long off = f.address - sec.shAddr;
                symKeys.add(off + "@" + sec.shName);
            }
        }

        List<FunctionInfo> result = new ArrayList<>(symtabFuncs);
        for (Long off : sweepHits) {
            String key = off + "@" + sec.shName;
            if (symKeys.contains(key)) continue;
            symKeys.add(key);

            long addr = sec.shAddr + off;
            // 限定函数大小为下一个函数或节区末尾
            long nextAddr = sec.shAddr + sec.shSize;
            for (FunctionInfo f : symtabFuncs) {
                if (f.address > addr && f.address < nextAddr) nextAddr = f.address;
            }
            long size = Math.min(nextAddr - addr, 4 * 1024);
            if (size <= 0) continue;

            long fileOff = sec.shOffset + off;
            if (fileOff < 0 || fileOff + size > data.length) continue;

            byte[] code = new byte[(int) size];
            System.arraycopy(data, (int) fileOff, code, 0, (int) size);

            // 对 ARM 重新探测 thumb 模式
            if (machineType == ElfConstants.EM_ARM) {
                disasm.setThumb(Disassembler.looksLikeThumb(code));
            }

            List<DisassembledInstruction> insns = disasm.disassemble(code, addr);
            FunctionInfo fi = new FunctionInfo("sub_" + Long.toHexString(addr), addr, size, sec.name);
            fi.instructions = insns;
            fi.source = SOURCE_LINEARSWEEP;
            fi.isThumb = Disassembler.looksLikeThumb(code);
            result.add(fi);
        }
        return result;
    }
}
