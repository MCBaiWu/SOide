package com.soide.elf.pseudoc;

import com.soide.elf.DisassembledInstruction;

import java.util.List;

/**
 * 伪 C 反汇编接口。
 * <p>
 * 设计原则：
 * - 输入：函数基本信息 + 反汇编指令列表 + 上下文（机器、调用约定等）
 * - 输出：人类可读的伪 C 文本（一行行字符串）
 * - 实现方式：基于指令助记符和操作数的模式匹配 + 调用约定推断
 * <p>
 * 后续要支持更复杂的反编译器时，只需要新增实现并在
 * {@link PseudoCRegistry#create} 里选择即可，无需修改 UI 层。
 */
public interface PseudoCConverter {

    /**
     * 把一个函数的反汇编指令转换为伪 C 文本。
     *
     * @param ctx 函数反汇编上下文
     * @return 伪 C 文本（已分行的字符串列表）
     */
    List<String> convert(PseudoCContext ctx);

    /** 该实现的名字（用于 UI 显示） */
    String name();

    /**
     * 上下文：提供给转换器所需的所有信息。
     */
    final class PseudoCContext {
        public String functionName;
        public long functionAddress;
        public long functionSize;
        public int machine;          // ElfConstants.EM_*
        public boolean isThumb;      // ARM32 Thumb 模式
        public List<DisassembledInstruction> instructions;
        public java.util.Map<Long, String> labels; // addr -> 名字
        public java.util.Map<Long, String> imports; // 跳转目标 -> 导入符号名

        public PseudoCContext(String functionName, long functionAddress, long functionSize,
                              int machine, boolean isThumb,
                              List<DisassembledInstruction> instructions) {
            this.functionName = functionName;
            this.functionAddress = functionAddress;
            this.functionSize = functionSize;
            this.machine = machine;
            this.isThumb = isThumb;
            this.instructions = instructions;
        }
    }
}
