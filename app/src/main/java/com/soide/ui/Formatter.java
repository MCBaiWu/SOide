package com.soide.ui;

import com.soide.elf.DynamicEntry;
import com.soide.elf.ElfFile;
import com.soide.elf.ExtractedString;
import com.soide.elf.ProgramHeader;
import com.soide.elf.RelocationEntry;
import com.soide.elf.SectionHeader;
import com.soide.elf.SymbolEntry;

/**
 * 把 ElfFile 各个子结构格式化为可显示的文本
 */
public class Formatter {

    public static String formatHeader(ElfFile elf) {
        return elf != null && elf.header != null ? elf.header.toString() : "(无内容)";
    }

    public static String formatProgramHeaders(ElfFile elf) {
        if (elf == null || elf.programHeaders == null || elf.programHeaders.isEmpty()) {
            return "(无程序头)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========== Program Headers ==========\n");
        sb.append(String.format("%-16s %-8s %-12s %-12s %-8s %-8s %s\n",
                "Type", "Offset", "VirtAddr", "PhysAddr", "FileSiz", "MemSiz", "Flg"));
        for (ProgramHeader ph : elf.programHeaders) {
            sb.append(ph.toString()).append("\n");
        }
        return sb.toString();
    }

    public static String formatSectionHeaders(ElfFile elf) {
        if (elf == null || elf.sectionHeaders == null || elf.sectionHeaders.isEmpty()) {
            return "(无节区头)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========== Section Headers ==========\n");
        sb.append(String.format("[%2s] %-20s %-14s %10s %-12s %-12s %s\n",
                "Nr", "Name", "Type", "Size", "Addr", "Offset", "Flags"));
        for (int i = 0; i < elf.sectionHeaders.size(); i++) {
            SectionHeader sh = elf.sectionHeaders.get(i);
            sb.append(String.format("[%2d] %s\n", i, sh.toString()));
        }
        return sb.toString();
    }

    public static String formatSymbols(ElfFile elf) {
        if (elf == null) return "(无符号表)";
        StringBuilder sb = new StringBuilder();
        boolean any = false;

        if (elf.symtabEntries != null && !elf.symtabEntries.isEmpty()) {
            any = true;
            sb.append("========== .symtab (静态符号表) ==========\n");
            sb.append(String.format("%-6s %-8s %-10s %-40s %-12s %s\n",
                    "Bind", "Type", "Ndx", "Name", "Value", "Size"));
            for (SymbolEntry se : elf.symtabEntries) {
                if (se.name == null || se.name.isEmpty()) continue;
                sb.append(String.format("%-6s %-8s [%4d]  %-40s 0x%08x %6d\n",
                        se.getBindName(), se.getTypeName(), se.stShndx,
                        se.name, se.stValue, se.stSize));
            }
            sb.append("\n");
        }

        if (elf.dynsymEntries != null && !elf.dynsymEntries.isEmpty()) {
            any = true;
            sb.append("========== .dynsym (动态符号表) ==========\n");
            sb.append(String.format("%-6s %-8s %-10s %-40s %-12s %s\n",
                    "Bind", "Type", "Ndx", "Name", "Value", "Size"));
            for (SymbolEntry se : elf.dynsymEntries) {
                if (se.name == null || se.name.isEmpty()) continue;
                sb.append(String.format("%-6s %-8s [%4d]  %-40s 0x%08x %6d\n",
                        se.getBindName(), se.getTypeName(), se.stShndx,
                        se.name, se.stValue, se.stSize));
            }
            sb.append("\n");
        }

        if (!any) return "(无符号表)";
        return sb.toString();
    }

    public static String formatStrings(ElfFile elf) {
        if (elf == null || elf.strings == null || elf.strings.isEmpty()) {
            return "(未提取到可打印字符串)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========== Extracted Strings (").append(elf.strings.size()).append(") ==========\n");
        sb.append(String.format("%-12s %-12s %-16s %s\n",
                "Offset", "Address", "Section", "Value"));
        for (ExtractedString s : elf.strings) {
            String display = s.value.length() > 200 ? s.value.substring(0, 200) + "..." : s.value;
            sb.append(String.format("0x%08x   0x%08x  %-16s \"%s\"\n",
                    s.offset, s.address, s.sectionName, display));
        }
        return sb.toString();
    }

    public static String formatRelocations(ElfFile elf) {
        if (elf == null || elf.relocations == null || elf.relocations.isEmpty()) {
            return "(无重定位条目)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========== Relocations (").append(elf.relocations.size()).append(") ==========\n");
        sb.append(String.format("%-12s %-14s %-32s\n", "Offset", "Type", "Symbol"));
        for (RelocationEntry r : elf.relocations) {
            sb.append(r.toString()).append("\n");
        }
        return sb.toString();
    }

    public static String formatDynamic(ElfFile elf) {
        if (elf == null || elf.dynamicEntries == null || elf.dynamicEntries.isEmpty()) {
            return "(无动态段)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("========== Dynamic Section ==========\n");
        for (DynamicEntry de : elf.dynamicEntries) {
            sb.append(de.toString()).append("\n");
        }
        sb.append("\n========== 依赖库 (NEEDED) ==========\n");
        if (elf.neededLibraries == null || elf.neededLibraries.isEmpty()) {
            sb.append("(无)\n");
        } else {
            for (String lib : elf.neededLibraries) {
                sb.append("  ").append(lib).append("\n");
            }
        }
        return sb.toString();
    }
}