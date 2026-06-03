package com.soide.elf;

import com.soide.nativebridge.NativeBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 反汇编器。优先使用 native bridge（NDK Capstone），失败时退回单指令逐条尝试。
 * <p>
 * v1.4.3 改进：放弃 Java Capstone 绑定（在该库某些架构/模式下抛 "capstone.Capstone"），
 * 改用项目内 {@link NativeBridge} 调真 Capstone，并按 C++ 参考实现的"逐条反汇编 + 失败跳过"逻辑
 * 保证遇到不可解码字节时不丢失后续指令。
 * <p>
 * ARM32 自动识别 Thumb 模式：可通过 {@link #setThumb(boolean)} 强制设置。
 */
public class Disassembler {

    private final int machineType;
    private final boolean is64Bit;
    private boolean thumb = false;

    public Disassembler(int elfMachine, boolean is64Bit) {
        this.machineType = elfMachine;
        this.is64Bit = is64Bit;
    }

    /** 设置是否为 Thumb 模式 (仅对 ARM32 有效)。 */
    public void setThumb(boolean thumb) {
        if (machineType == ElfConstants.EM_ARM) this.thumb = thumb;
    }

    public boolean isThumb() { return thumb; }
    public int getMachineType() { return machineType; }

    /** 探测代码块是否像 Thumb 模式 (基于前两个字节是否为 push prologue 0xb5xx)。 */
    public static boolean looksLikeThumb(byte[] code) {
        if (code == null || code.length < 2) return false;
        int b0 = code[0] & 0xff;
        int b1 = code[1] & 0xff;
        if (b0 == 0xb5) return true;
        if ((b0 & 0xf8) == 0x48) return true;
        return false;
    }

    /**
     * 反汇编整段字节码。
     * 1) 先尝试整段 native 反汇编（最高效）；2) 失败则按 4/2/16 字节步长逐条尝试。
     */
    public List<DisassembledInstruction> disassemble(byte[] code, long address) {
        List<DisassembledInstruction> result = new ArrayList<>();
        if (code == null || code.length == 0) return result;

        // 1) native bridge
        List<NativeBridge.DisasmResult> nativeList = nativeDisasm(code, address);
        if (nativeList != null && !nativeList.isEmpty()) {
            for (NativeBridge.DisasmResult r : nativeList) {
                result.add(new DisassembledInstruction(r.address, r.bytes, r.mnemonic, r.opStr));
            }
            return result;
        }

        // 2) 整段失败 → 逐条尝试
        return disassembleByStep(code, address);
    }

    /** 调 native bridge 选合适函数 */
    private List<NativeBridge.DisasmResult> nativeDisasm(byte[] code, long address) {
        if (machineType == ElfConstants.EM_AARCH64) {
            return NativeBridge.disasmArm64(code, address);
        } else if (machineType == ElfConstants.EM_ARM) {
            return NativeBridge.disasm(code, address, thumb);
        } else {
            return NativeBridge.disasm(code, address, false);
        }
    }

    /**
     * 逐条反汇编：C++ 参考实现风格。固定步长 4(ARM) / 2(Thumb) / 4(AArch64) / 16(x86)。
     * 解码失败则跳过一个字节，避免坏字节导致整段失败。
     */
    public List<DisassembledInstruction> disassembleByStep(byte[] code, long address) {
        List<DisassembledInstruction> result = new ArrayList<>();
        if (code == null || code.length == 0) return result;

        int step = stepSize();
        int off = 0;
        while (off < code.length) {
            int trySize = Math.min(16, code.length - off);
            byte[] window = new byte[trySize];
            System.arraycopy(code, off, window, 0, trySize);
            List<NativeBridge.DisasmResult> one = nativeDisasm(window, address + off);
            if (one != null && !one.isEmpty()) {
                NativeBridge.DisasmResult r = one.get(0);
                if (r.size > 0) {
                    byte[] bytes = new byte[Math.min(r.size, 8)];
                    for (int k = 0; k < bytes.length && k < r.bytes.length; k++) bytes[k] = r.bytes[k];
                    result.add(new DisassembledInstruction(r.address, bytes, r.mnemonic, r.opStr));
                    off += r.size;
                    continue;
                }
            }
            // 解码失败：跳过 1 字节
            off += 1;
        }
        return result;
    }

    private int stepSize() {
        if (machineType == ElfConstants.EM_ARM) return thumb ? 2 : 4;
        if (machineType == ElfConstants.EM_386 || machineType == ElfConstants.EM_X86_64) return 1;
        return 4;
    }

    /**
     * v1.4.3 新增：线性扫描可执行段以发现额外函数（参考 C++ 实现）。
     * 在 .text 等 SHF_EXECINSTR 节区上滑动，对每条指令调用 native bridge。
     * 已经识别过的地址用 {@code seen} 跳过。
     *
     * @param baseAddr 节区起始虚地址
     * @param baseFileOff 节区起始文件偏移
     * @param code 节区原始字节
     * @param seen 已识别地址集合（会被原地扩充）
     * @param scanThumb 该节区是否使用 Thumb 模式 (ARM32)
     */
    public List<DisassembledInstruction> linearSweep(long baseAddr, long baseFileOff,
                                                     byte[] code, java.util.Set<Long> seen,
                                                     boolean scanThumb) {
        List<DisassembledInstruction> result = new ArrayList<>();
        if (code == null || code.length == 0) return result;
        boolean prevThumb = this.thumb;
        this.thumb = scanThumb;
        try {
            int off = 0;
            int step = stepSize();
            while (off < code.length) {
                long va = baseAddr + off;
                if (seen.contains(va)) { off += 1; continue; }
                int trySize = Math.min(16, code.length - off);
                if (trySize <= 0) break;
                byte[] window = new byte[trySize];
                System.arraycopy(code, off, window, 0, trySize);
                List<NativeBridge.DisasmResult> one = nativeDisasm(window, va);
                if (one != null && !one.isEmpty()) {
                    NativeBridge.DisasmResult r = one.get(0);
                    if (r.size > 0) {
                        byte[] bytes = new byte[Math.min(r.size, 8)];
                        for (int k = 0; k < bytes.length && k < r.bytes.length; k++) bytes[k] = r.bytes[k];
                        result.add(new DisassembledInstruction(r.address, bytes, r.mnemonic, r.opStr));
                        seen.add(va);
                        // 标记整条指令覆盖的字节
                        for (int k = 1; k < r.size; k++) seen.add(va + k);
                        off += r.size;
                        continue;
                    }
                }
                off += 1;
            }
        } finally {
            this.thumb = prevThumb;
        }
        return result;
    }
}
