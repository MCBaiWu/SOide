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
    public List<ExtractedString> strings;
    public List<RelocationEntry> relocations;
    public List<FunctionInfo> functions;
    public List<ImportedFunction> imports;
    public HashLookup gnuHash;
    public HashLookup sysvHash;
}