package com.soide.ui;

import com.soide.elf.FunctionInfo;

import java.io.Serializable;

/**
 * 函数详情 activity 的数据载体。
 * 包含 disasm + 周围上下文。
 */
public class FuncDetailData implements Serializable {
    public FunctionInfo function;
    /** 全局符号表（供交叉引用查找） */
    public java.util.List<com.soide.elf.SymbolEntry> symbols;
    /** 导入符号 (PLT 桩) */
    public java.util.List<com.soide.elf.ImportedFunction> imports;
    /** 机器类型 (ElfConstants.EM_*) */
    public int machine;
}
