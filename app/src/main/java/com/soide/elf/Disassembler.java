package com.soide.elf;

import java.util.ArrayList;
import java.util.List;

import capstone.Capstone;

/**
 * Capstone 反汇编器封装
 * 支持 ARM, AArch64, x86, x86_64 四种常见架构
 */
public class Disassembler {

    private final int arch;
    private final int mode;
    private final int machineType;

    public Disassembler(int elfMachine, boolean is64Bit) {
        this.machineType = elfMachine;
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
                // 默认按 64 位小端 ARM 处理
                this.arch = Capstone.CS_ARCH_ARM64;
                this.mode = Capstone.CS_MODE_ARM;
        }
    }

    public String getArchName() {
        return ElfConstants.getMachineName(machineType);
    }

    /**
     * 反汇编一段字节码
     *
     * @param code    机器码
     * @param address 起始地址
     * @return 反汇编后的指令列表
     */
    public List<DisassembledInstruction> disassemble(byte[] code, long address) {
        List<DisassembledInstruction> result = new ArrayList<>();
        if (code == null || code.length == 0) {
            return result;
        }

        try {
            Capstone cs = new Capstone(arch, mode);
            Capstone.CsInsn[] insns = cs.disasm(code, address);
            for (Capstone.CsInsn insn : insns) {
                result.add(new DisassembledInstruction(
                        insn.address,
                        insn.bytes,
                        insn.mnemonic,
                        insn.opStr
                ));
            }
        } catch (Throwable t) {
            // 反汇编失败时降级为单条 .byte 指令
            result.add(new DisassembledInstruction(
                    address, code, "byte", "(disassembly failed: " + t.getMessage() + ")"));
        }

        return result;
    }
}