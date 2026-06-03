package com.soide.ui;

import com.soide.elf.ElfFile;

/**
 * 全局静态 Holder，用于在 Activity 和 Fragment 之间共享 ELF 解析结果
 */
public final class ParseResultHolder {

    private static ElfFile elfFile;

    private ParseResultHolder() {}

    public static void set(ElfFile file) {
        elfFile = file;
    }

    public static ElfFile get() {
        return elfFile;
    }

    public static void clear() {
        elfFile = null;
    }
}