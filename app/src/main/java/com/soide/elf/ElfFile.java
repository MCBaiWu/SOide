package com.soide.elf;

import java.util.List;

/**
 * 保存完整 ELF 解析结果的数据类
 */
public class ElfFile {

    public String filePath;
    public ElfHeader header;
    public List<SectionHeader> sectionHeaders;
    public List<ProgramHeader> programHeaders;
    public List<SymbolEntry> symtabEntries;
    public List<SymbolEntry> dynsymEntries;
    public List<DynamicEntry> dynamicEntries;
    public List<String> neededLibraries;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // ELF Header
        sb.append(header.toString()).append("\n");

        // Program Headers
        if (programHeaders != null && !programHeaders.isEmpty()) {
            sb.append("========== Program Headers ==========\n");
            sb.append(String.format("%-16s %-8s %-10s %-10s %-8s %-8s %s\n",
                    "Type", "Offset", "VirtAddr", "PhysAddr", "FileSiz", "MemSiz", "Flg"));
            for (ProgramHeader ph : programHeaders) {
                sb.append(ph.toString()).append("\n");
            }
            sb.append("\n");
        }

        // Section Headers
        if (sectionHeaders != null && !sectionHeaders.isEmpty()) {
            sb.append("========== Section Headers ==========\n");
            sb.append("[Nr] Name                Type             Size     Addr     Offset   Flags\n");
            for (SectionHeader sh : sectionHeaders) {
                sb.append(sh.toString()).append("\n");
            }
            sb.append("\n");
        }

        // Dynamic Symbol Table
        if (dynsymEntries != null && !dynsymEntries.isEmpty()) {
            sb.append("========== Dynamic Symbol Table ==========\n");
            sb.append("Bind   Type     Name                                       Value      Size\n");
            for (SymbolEntry se : dynsymEntries) {
                sb.append(se.toString()).append("\n");
            }
            sb.append("\n");
        }

        // Dynamic Entries
        if (dynamicEntries != null && !dynamicEntries.isEmpty()) {
            sb.append("========== Dynamic Section ==========\n");
            for (DynamicEntry de : dynamicEntries) {
                sb.append(de.toString()).append("\n");
            }
            sb.append("\n");
        }

        // Needed libraries
        if (neededLibraries != null && !neededLibraries.isEmpty()) {
            sb.append("========== 依赖库 ==========\n");
            for (String lib : neededLibraries) {
                sb.append("  ").append(lib).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}