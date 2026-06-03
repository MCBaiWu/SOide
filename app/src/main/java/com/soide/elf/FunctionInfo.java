package com.soide.elf;

import java.util.List;

/**
 * 函数信息（带反汇编）
 */
public class FunctionInfo {

    public String name;          // 函数名
    public long address;         // 函数起始地址
    public long size;            // 函数大小（字节）
    public String sectionName;   // 所属节区
    public List<DisassembledInstruction> instructions; // 反汇编结果

    public FunctionInfo(String name, long address, long size, String sectionName) {
        this.name = name;
        this.address = address;
        this.size = size;
        this.sectionName = sectionName;
    }
}