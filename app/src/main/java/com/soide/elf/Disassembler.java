package com.soide.elf;

import java.util.ArrayList;
import java.util.List;
import capstone.Capstone;
import capstone.api.Instruction;

/**
 * 反汇编器。支持 ARM (含 Thumb 模式自动识别)、AArch64、x86、x86_64。
 * <p>
 * ARM32 架构下，如果数据起始字节是 thumb 序言 (如 push ..., 即 0xb5xx)，或符号 LSB=1，
 * 可通过 {@link #setThumb(boolean)} 强制设置 thumb 模式，按 2 字节切分机器码。
 */
public class Disassembler {

    private final int arch;
    private int mode;
    private final int machineType;
    private boolean is64Bit;

    public Disassembler(int elfMachine, boolean is64Bit) {
        this.machineType = elfMachine;
        this.is64Bit = is64Bit;
        switch (elfMachine) {
            case ElfConstants.EM_ARM:
                this.arch = Capstone.CS_ARCH_ARM;
                this.mode = Capstone.CS_MODE_ARM;
                break;
            case ElfConstants.EM_AARCH64:
                this.arch = Capstone.CS_ARCH_ARM64;
                this.mode = Capstone.CS_MODE_ARM;
                break;
            case ElfConstants.EM_386:
                this.arch = Capstone.CS_ARCH_X86;
                this.mode = Capstone.CS_MODE_32;
                break;
            case ElfConstants.EM_X86_64:
                this.arch = Capstone.CS_ARCH_X86;
                this.mode = Capstone.CS_MODE_64;
                break;
            default:
                this.arch = Capstone.CS_ARCH_ARM64;
                this.mode = Capstone.CS_MODE_ARM;
        }
    }

    /** 设置是否为 Thumb 模式 (仅对 ARM32 有效)。 */
    public void setThumb(boolean thumb) {
        if (machineType == ElfConstants.EM_ARM) {
            this.mode = thumb ? Capstone.CS_MODE_THUMB : Capstone.CS_MODE_ARM;
        }
    }

    /** 探测代码块是否像 Thumb 模式 (基于前两个字节是否为 push prologue 0xb5xx)。 */
    public static boolean looksLikeThumb(byte[] code) {
        if (code == null || code.length < 2) return false;
        int b0 = code[0] & 0xff;
        int b1 = code[1] & 0xff;
        // Thumb push 0xb500-0xb5ff
        if (b0 == 0xb5) return true;
        // Thumb nop: 0x46c0 (mov r8, r8 等价) - 不是强信号
        // Thumb 0x4?00 格式 (mov rd, rs)
        if ((b0 & 0xf8) == 0x48) return true; // 0x48xx 系列 (ldmia, ldr literal)
        return false;
    }

    public List<DisassembledInstruction> disassemble(byte[] code, long address) {
        List<DisassembledInstruction> result = new ArrayList<>();
        if (code == null || code.length == 0) return result;

        Capstone cs = null;
        try {
            cs = new Capstone(arch, mode);
            // 在 Thumb 模式下告知 Capstone 数据按 2 字节对齐
            if (machineType == ElfConstants.EM_ARM
                    && (mode & Capstone.CS_MODE_THUMB) != 0) {
                // Capstone 自动按 thumb 解码
            }
            Instruction[] insns = cs.disasm(code, address);
            for (Instruction insn : insns) {
                long addr;
                byte[] bytes;
                String mnemonic;
                String opStr;
                if (insn instanceof Capstone.CsInsn) {
                    Capstone.CsInsn csi = (Capstone.CsInsn) insn;
                    addr = csi.getAddress();
                    bytes = csi.bytes;
                    mnemonic = csi.mnemonic;
                    opStr = csi.opStr;
                } else {
                    addr = insn.getAddress();
                    bytes = insn.getBytes();
                    mnemonic = insn.getMnemonic();
                    opStr = insn.getOpStr();
                }
                result.add(new DisassembledInstruction(addr, bytes, mnemonic, opStr));
            }
        } catch (Throwable t) {
            result.add(new DisassembledInstruction(
                    address, code, "byte", "(disassembly failed: " + t.getMessage() + ")"));
        } finally {
            if (cs != null) try { cs.close(); } catch (Exception ignored) {}
        }
        return result;
    }

    public int getArch() {
        return arch;
    }

    public int getMode() {
        return mode;
    }

    public boolean is64Bit() {
        return is64Bit;
    }
}
