package com.soide.elf;

/**
 * 提取出的可打印字符串
 */
public class ExtractedString {

    public long offset;   // 字符串在文件中的偏移
    public long address;  // 字符串的虚拟地址（如果可知）
    public String value;  // 字符串内容
    public String sectionName; // 所属节区

    public ExtractedString(long offset, long address, String value, String sectionName) {
        this.offset = offset;
        this.address = address;
        this.value = value;
        this.sectionName = sectionName;
    }

    @Override
    public String toString() {
        return String.format("0x%08x: %s", offset, value);
    }
}