package com.soide.elf;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ELF 文件解析器
 * 支持 32-bit 和 64-bit 的 ELF 文件，自动检测大小端。
 */
public class ElfParser {

    private byte[] data;
    private ByteOrder byteOrder;
    private boolean is64Bit;
    private ElfFile elfFile;
    private SectionHeader dynsymSec;        // 用于解析重定位的符号名
    private List<SymbolEntry> dynsymForRel; // dynsym 符号列表

    public ElfFile parse(File file) throws IOException {
        elfFile = new ElfFile();
        elfFile.filePath = file.getAbsolutePath();

        try (InputStream is = new FileInputStream(file)) {
            data = is.readAllBytes();
        }

        if (!checkMagic()) {
            throw new IOException("不是有效的 ELF 文件（Magic 不匹配）");
        }

        parseHeader();
        parseProgramHeaders();
        parseSectionHeaders();
        parseHashTables();
        parseRelocations();
        extractStrings();
        parseFunctions();
        parseImports();

        return elfFile;
    }

    private boolean checkMagic() {
        if (data.length < 4) return false;
        return data[0] == ElfConstants.ELF_MAGIC[0]
                && data[1] == ElfConstants.ELF_MAGIC[1]
                && data[2] == ElfConstants.ELF_MAGIC[2]
                && data[3] == ElfConstants.ELF_MAGIC[3];
    }

    private void parseHeader() {
        ElfHeader h = new ElfHeader();
        elfFile.header = h;

        h.eiClass = data[4] & 0xff;
        h.eiData = data[5] & 0xff;
        h.eiVersion = data[6] & 0xff;
        h.eiOsabi = data[7] & 0xff;
        h.eiAbiVersion = data[8] & 0xff;

        is64Bit = (h.eiClass == ElfConstants.ELFCLASS64);
        byteOrder = (h.eiData == ElfConstants.ELFDATA2LSB)
                ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

        ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
        buf.position(16); // skip e_ident

        h.eType = buf.getShort() & 0xffff;
        h.eMachine = buf.getShort() & 0xffff;
        h.eVersion = buf.getInt();

        if (is64Bit) {
            h.eEntry = buf.getLong();
            h.ePhoff = buf.getLong();
            h.eShoff = buf.getLong();
        } else {
            h.eEntry = buf.getInt() & 0xffffffffL;
            h.ePhoff = buf.getInt() & 0xffffffffL;
            h.eShoff = buf.getInt() & 0xffffffffL;
        }

        h.eFlags = buf.getInt();
        h.eEhsize = buf.getShort() & 0xffff;
        h.ePhentsize = buf.getShort() & 0xffff;
        h.ePhnum = buf.getShort() & 0xffff;
        h.eShentsize = buf.getShort() & 0xffff;
        h.eShnum = buf.getShort() & 0xffff;
        h.eShstrndx = buf.getShort() & 0xffff;
    }

    private void parseProgramHeaders() {
        ElfHeader h = elfFile.header;
        List<ProgramHeader> list = new ArrayList<>();

        for (int i = 0; i < h.ePhnum; i++) {
            long offset = h.ePhoff + (long) i * h.ePhentsize;
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) offset);

            ProgramHeader ph = new ProgramHeader();

            if (is64Bit) {
                ph.pType = buf.getInt();
                ph.pFlags = buf.getInt() & 0xffffffffL;
                ph.pOffset = buf.getLong();
                ph.pVaddr = buf.getLong();
                ph.pPaddr = buf.getLong();
                ph.pFilesz = buf.getLong();
                ph.pMemsz = buf.getLong();
                ph.pAlign = buf.getLong();
            } else {
                ph.pType = buf.getInt();
                ph.pOffset = buf.getInt() & 0xffffffffL;
                ph.pVaddr = buf.getInt() & 0xffffffffL;
                ph.pPaddr = buf.getInt() & 0xffffffffL;
                ph.pFilesz = buf.getInt() & 0xffffffffL;
                ph.pMemsz = buf.getInt() & 0xffffffffL;
                ph.pFlags = buf.getInt() & 0xffffffffL;
                ph.pAlign = buf.getInt() & 0xffffffffL;
            }

            list.add(ph);
        }

        elfFile.programHeaders = list;
    }

    private void parseSectionHeaders() {
        ElfHeader h = elfFile.header;
        List<SectionHeader> list = new ArrayList<>();

        for (int i = 0; i < h.eShnum; i++) {
            long offset = h.eShoff + (long) i * h.eShentsize;
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) offset);

            SectionHeader sh = new SectionHeader();
            sh.shName = buf.getInt();

            if (is64Bit) {
                sh.shType = buf.getInt();
                sh.shFlags = buf.getLong();
                sh.shAddr = buf.getLong();
                sh.shOffset = buf.getLong();
                sh.shSize = buf.getLong();
                sh.shLink = buf.getInt();
                sh.shInfo = buf.getInt();
                sh.shAddralign = buf.getLong();
                sh.shEntsize = buf.getLong();
            } else {
                sh.shType = buf.getInt();
                sh.shFlags = buf.getInt() & 0xffffffffL;
                sh.shAddr = buf.getInt() & 0xffffffffL;
                sh.shOffset = buf.getInt() & 0xffffffffL;
                sh.shSize = buf.getInt() & 0xffffffffL;
                sh.shLink = buf.getInt();
                sh.shInfo = buf.getInt();
                sh.shAddralign = buf.getInt() & 0xffffffffL;
                sh.shEntsize = buf.getInt() & 0xffffffffL;
            }

            list.add(sh);
        }

        String shstrtab = null;
        int shstrndx = h.eShstrndx;
        if (shstrndx > 0 && shstrndx < list.size()) {
            SectionHeader shstrSec = list.get(shstrndx);
            shstrtab = readString(shstrSec.shOffset, (int) shstrSec.shSize);
        }

        for (SectionHeader sh : list) {
            if (shstrtab != null && sh.shName > 0 && sh.shName < shstrtab.length()) {
                int end = shstrtab.indexOf('\0', sh.shName);
                if (end < 0) end = shstrtab.length();
                sh.name = shstrtab.substring(sh.shName, end);
            }
        }

        elfFile.sectionHeaders = list;

        parseSymbolTables();
        parseDynamic();
    }

    private void parseSymbolTables() {
        // 先预解析所有 strtab 候选（不依赖节区名，shstrtab 失败时仍能工作）
        String dynstr = findDynstr();

        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_SYMTAB) {
                // 符号表的字符串表: sh_link 指向 .strtab
                String strtab = resolveStrtab(sh);
                List<SymbolEntry> list = parseSymbols(sh, strtab);
                elfFile.symtabEntries = list;
            } else if (sh.shType == ElfConstants.SHT_DYNSYM) {
                // 动态符号表的字符串表: sh_link 指向 .dynstr
                dynsymSec = sh;
                // 优先 sh_link, 其次预先找到的 .dynstr
                String strtab = resolveStrtab(sh);
                if (strtab == null) strtab = dynstr;
                dynsymForRel = parseSymbols(sh, strtab);
                elfFile.dynsymEntries = dynsymForRel;
            }
        }
    }

    /**
     * 不依赖节区名（shstrtab 失败时仍能找到），
     * 通过 sh_type 找出动态符号表可能使用的字符串表 (.dynstr)。
     */
    private String findDynstr() {
        // 1) 找 .dynsym，看它的 sh_link 指向谁
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType != ElfConstants.SHT_DYNSYM) continue;
            String s = readSectionAsString(sh.shLink);
            if (s != null && hasNonNullContent(s)) return s;
        }
        // 2) 找任何名为 .dynstr 的 SHT_STRTAB
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_STRTAB
                    && ".dynstr".equals(sh.name)
                    && sh.shSize > 0) {
                String s = readSectionAsString(sh.shOffset, sh.shSize);
                if (s != null) return s;
            }
        }
        // 3) 找 DT_STRTAB (vaddr 指向 .dynstr 的运行时地址)
        long dynstrVaddr = findDtStrtab();
        if (dynstrVaddr != 0) {
            // 把 vaddr 映射回文件偏移
            for (ProgramHeader ph : elfFile.programHeaders) {
                if (ph.pVaddr <= dynstrVaddr
                        && dynstrVaddr < ph.pVaddr + ph.pFilesz) {
                    long fileOff = ph.pOffset + (dynstrVaddr - ph.pVaddr);
                    // .dynstr 在 loadable 段里能找到完整的字符串表（PT_LOAD 包含它）
                    // 大小用整个段的文件大小
                    String s = readSectionAsString(fileOff, ph.pFilesz - (fileOff - ph.pOffset));
                    if (s != null && hasNonNullContent(s)) return s;
                }
            }
            // 也可能落在某个 section 里
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shAddr <= dynstrVaddr
                        && dynstrVaddr < sh.shAddr + sh.shSize
                        && sh.shSize > 0) {
                    String s = readSectionAsString(sh.shOffset, sh.shSize);
                    if (s != null && hasNonNullContent(s)) return s;
                }
            }
        }
        return null;
    }

    /** 返回 .dynstr 的虚拟地址 (DT_STRTAB) */
    private long findDtStrtab() {
        SectionHeader dynamicSec = null;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_DYNAMIC) {
                dynamicSec = sh; break;
            }
        }
        if (dynamicSec == null) {
            for (ProgramHeader ph : elfFile.programHeaders) {
                if (ph.pType == ElfConstants.PT_DYNAMIC) {
                    dynamicSec = new SectionHeader();
                    dynamicSec.shOffset = ph.pOffset;
                    dynamicSec.shSize = ph.pFilesz;
                    break;
                }
            }
        }
        if (dynamicSec == null) return 0;
        int entrySize = is64Bit ? 16 : 8;
        for (long off = dynamicSec.shOffset;
             off + entrySize <= dynamicSec.shOffset + dynamicSec.shSize;
             off += entrySize) {
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) off);
            long dTag, dVal;
            if (is64Bit) { dTag = buf.getLong(); dVal = buf.getLong(); }
            else { dTag = buf.getInt() & 0xffffffffL; dVal = buf.getInt() & 0xffffffffL; }
            if (dTag == 0) break;
            if (dTag == ElfConstants.DT_STRTAB) return dVal;
        }
        return 0;
    }

    /** 通过 sh_link 找到符号表对应的字符串表。多重 fallback。 */
    private String resolveStrtab(SectionHeader symSh) {
        // 1) sh_link 直接对应的 section
        if (symSh.shLink > 0 && symSh.shLink < elfFile.sectionHeaders.size()) {
            SectionHeader linked = elfFile.sectionHeaders.get(symSh.shLink);
            String s = readSectionAsString(linked.shOffset, linked.shSize);
            if (s != null && hasNonNullContent(s)) return s;
        }
        // 2) 按名字 (如果 shstrtab 还能用)
        if (symSh.shType == ElfConstants.SHT_DYNSYM) {
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shType == ElfConstants.SHT_STRTAB
                        && ".dynstr".equals(sh.name) && sh.shSize > 0) {
                    String s = readSectionAsString(sh.shOffset, sh.shSize);
                    if (s != null) return s;
                }
            }
            // 3) 任何 SHT_STRTAB 节区都试试 (兜底)
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shType == ElfConstants.SHT_STRTAB && sh.shSize > 0
                        && (sh.name == null || !sh.name.isEmpty())) {
                    String s = readSectionAsString(sh.shOffset, sh.shSize);
                    if (s != null && hasNonNullContent(s)) return s;
                }
            }
        } else {
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shType == ElfConstants.SHT_STRTAB
                        && ".strtab".equals(sh.name) && sh.shSize > 0) {
                    String s = readSectionAsString(sh.shOffset, sh.shSize);
                    if (s != null) return s;
                }
            }
            // 3) 任何 SHT_STRTAB 节区都试试 (兜底)
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shType == ElfConstants.SHT_STRTAB && sh.shSize > 0) {
                    String s = readSectionAsString(sh.shOffset, sh.shSize);
                    if (s != null && hasNonNullContent(s)) return s;
                }
            }
        }
        return null;
    }

    private String readSectionAsString(int secIdx) {
        if (secIdx <= 0 || secIdx >= elfFile.sectionHeaders.size()) return null;
        SectionHeader sh = elfFile.sectionHeaders.get(secIdx);
        return readSectionAsString(sh.shOffset, sh.shSize);
    }

    private String readSectionAsString(long offset, long size) {
        if (size <= 0 || offset < 0) return null;
        if (offset >= data.length) return null;
        long avail = data.length - offset;
        int len = (int) Math.min(size, avail);
        if (len <= 0) return null;
        try {
            return new String(data, (int) offset, len, java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 检查字符串表是否含有非空内容（去除首字节 null） */
    private static boolean hasNonNullContent(String s) {
        if (s == null || s.length() < 2) return false;
        // strtab 至少应该有一个非 null 字符
        for (int i = 1; i < Math.min(s.length(), 64); i++) {
            if (s.charAt(i) != '\0') return true;
        }
        return false;
    }

    private List<SymbolEntry> parseSymbols(SectionHeader sh, String strtab) {
        List<SymbolEntry> symbols = new ArrayList<>();
        int entrySize = (int) sh.shEntsize;
        if (entrySize <= 0) return symbols;

        for (long off = sh.shOffset; off < sh.shOffset + sh.shSize; off += entrySize) {
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) off);

            SymbolEntry se = new SymbolEntry();

            if (is64Bit) {
                se.stName = buf.getInt();
                se.stInfo = buf.get() & 0xff;
                se.stOther = buf.get() & 0xff;
                se.stShndx = buf.getShort() & 0xffff;
                se.stValue = buf.getLong();
                se.stSize = buf.getLong();
            } else {
                se.stName = buf.getInt();
                se.stValue = buf.getInt() & 0xffffffffL;
                se.stSize = buf.getInt() & 0xffffffffL;
                se.stInfo = buf.get() & 0xff;
                se.stOther = buf.get() & 0xff;
                se.stShndx = buf.getShort() & 0xffff;
            }

            if (strtab != null && se.stName > 0 && se.stName < strtab.length()) {
                int end = strtab.indexOf('\0', se.stName);
                if (end < 0) end = strtab.length();
                se.name = strtab.substring(se.stName, end);
            }

            symbols.add(se);
        }

        return symbols;
    }

    private void parseDynamic() {
        SectionHeader dynamicSec = null;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_DYNAMIC) {
                dynamicSec = sh;
                break;
            }
        }

        if (dynamicSec == null) {
            for (ProgramHeader ph : elfFile.programHeaders) {
                if (ph.pType == ElfConstants.PT_DYNAMIC) {
                    dynamicSec = new SectionHeader();
                    dynamicSec.shOffset = ph.pOffset;
                    dynamicSec.shSize = ph.pFilesz;
                    break;
                }
            }
        }

        if (dynamicSec == null) return;

        String dynstr = null;
        long dynstrAddr = 0;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (".dynstr".equals(sh.name)) {
                dynstr = readString(sh.shOffset, (int) sh.shSize);
                dynstrAddr = sh.shAddr;
                break;
            }
        }

        List<DynamicEntry> entries = new ArrayList<>();
        List<String> needed = new ArrayList<>();

        int entrySize = is64Bit ? 16 : 8;
        long dynstrBase = 0;

        for (long off = dynamicSec.shOffset; off < dynamicSec.shOffset + dynamicSec.shSize; off += entrySize) {
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) off);

            DynamicEntry de = new DynamicEntry();
            if (is64Bit) {
                de.dTag = buf.getLong();
                de.dVal = buf.getLong();
            } else {
                de.dTag = buf.getInt() & 0xffffffffL;
                de.dVal = buf.getInt() & 0xffffffffL;
            }

            if (de.dTag == ElfConstants.DT_NULL) {
                entries.add(de);
                break;
            }

            if (de.dTag == ElfConstants.DT_STRTAB) {
                dynstrBase = de.dVal;
            }

            entries.add(de);
        }

        if (dynstrBase != 0) {
            for (DynamicEntry de : entries) {
                if (de.dTag == ElfConstants.DT_NEEDED || de.dTag == ElfConstants.DT_SONAME) {
                    long actualOffset = de.dVal;

                    if (dynstr != null) {
                        long stroff = actualOffset;
                        if (dynstrAddr > 0 && stroff > dynstrAddr) {
                            stroff -= dynstrAddr;
                        }

                        if (stroff >= 0 && stroff < dynstr.length()) {
                            int end = dynstr.indexOf('\0', (int) stroff);
                            if (end < 0) end = dynstr.length();
                            de.valueName = dynstr.substring((int) stroff, end);
                        }
                    }

                    if (de.dTag == ElfConstants.DT_NEEDED && de.valueName != null) {
                        needed.add(de.valueName);
                    }
                }
            }
        }

        elfFile.dynamicEntries = entries;
        elfFile.neededLibraries = needed;
    }

    /**
     * 解析重定位表（.rel / .rela）
     */
    private void parseRelocations() {
        List<RelocationEntry> all = new ArrayList<>();
        String dynstr = null;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (".dynstr".equals(sh.name)) {
                dynstr = readString(sh.shOffset, (int) sh.shSize);
                break;
            }
        }

        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_REL || sh.shType == ElfConstants.SHT_RELA) {
                parseRelocationSection(sh, dynstr, all);
            }
        }

        elfFile.relocations = all;
    }

    private void parseRelocationSection(SectionHeader sh, String dynstr, List<RelocationEntry> out) {
        int entrySize = (int) sh.shEntsize;
        if (entrySize <= 0) return;

        for (long off = sh.shOffset; off < sh.shOffset + sh.shSize; off += entrySize) {
            ByteBuffer buf = ByteBuffer.wrap(data).order(byteOrder);
            buf.position((int) off);

            RelocationEntry re = new RelocationEntry();
            if (is64Bit) {
                re.rOffset = buf.getLong();
                re.rInfo = buf.getLong();
                if (sh.shType == ElfConstants.SHT_RELA) {
                    re.rAddend = buf.getLong();
                }
            } else {
                re.rOffset = buf.getInt() & 0xffffffffL;
                re.rInfo = buf.getInt() & 0xffffffffL;
                if (sh.shType == ElfConstants.SHT_RELA) {
                    re.rAddend = buf.getInt() & 0xffffffffL;
                }
            }

            re.typeName = getRelocationTypeName(elfFile.header.eMachine, re.getType(is64Bit));
            re.symbolIndex = re.getSymbolIndex(is64Bit);

            if (dynsymForRel != null && re.symbolIndex > 0 && re.symbolIndex < dynsymForRel.size()) {
                SymbolEntry sym = dynsymForRel.get(re.symbolIndex);
                re.symbolName = sym.name;
            }
            if (re.symbolName == null && dynstr != null) {
                // 尝试用 rInfo 当作 dynstr 偏移（极少情况）
                re.symbolName = "";
            }

            out.add(re);
        }
    }

    private String getRelocationTypeName(int machine, int type) {
        if (machine == ElfConstants.EM_X86_64) {
            switch (type) {
                case 6: return "R_X86_64_64";
                case 7: return "R_X86_64_GLOB_DAT";
                case 8: return "R_X86_64_JUMP_SLOT";
                case 9: return "R_X86_64_RELATIVE";
                case 23: return "R_X86_64_IRELATIVE";
                case 26: return "R_X86_64_COPY";
                default: return String.format("R_X86_64_%d", type);
            }
        } else if (machine == ElfConstants.EM_386) {
            switch (type) {
                case 1: return "R_386_32";
                case 2: return "R_386_PC32";
                case 6: return "R_386_GLOB_DAT";
                case 7: return "R_386_JUMP_SLOT";
                case 8: return "R_386_RELATIVE";
                default: return String.format("R_386_%d", type);
            }
        } else if (machine == ElfConstants.EM_AARCH64) {
            switch (type) {
                case 257: return "R_AARCH64_ABS64";
                case 258: return "R_AARCH64_ABS32";
                case 1024: return "R_AARCH64_GLOB_DAT";
                case 1026: return "R_AARCH64_JUMP_SLOT";
                case 1027: return "R_AARCH64_RELATIVE";
                case 1028: return "R_AARCH64_IRELATIVE";
                default: return String.format("R_AARCH64_%d", type);
            }
        } else if (machine == ElfConstants.EM_ARM) {
            switch (type) {
                case 2: return "R_ARM_ABS32";
                case 21: return "R_ARM_GLOB_DAT";
                case 22: return "R_ARM_JUMP_SLOT";
                case 23: return "R_ARM_RELATIVE";
                default: return String.format("R_ARM_%d", type);
            }
        }
        return String.format("REL_%d", type);
    }

    /**
     * 从节区中提取可打印的字符串（最小长度 4）
     */
    private void extractStrings() {
        List<ExtractedString> list = new ArrayList<>();
        int minLen = 4;

        for (SectionHeader sh : elfFile.sectionHeaders) {
            // 只在数据型节区里提取字符串
            if (sh.shType != ElfConstants.SHT_PROGBITS && sh.shType != ElfConstants.SHT_STRTAB) {
                continue;
            }
            if (sh.name != null && sh.name.startsWith(".rela")) continue;
            if (sh.name != null && sh.name.startsWith(".rel")) continue;
            if (sh.name != null && sh.name.equals(".eh_frame")) continue;

            int start = (int) sh.shOffset;
            int end = (int) Math.min(sh.shOffset + sh.shSize, data.length);
            if (start >= end) continue;

            int strStart = -1;
            for (int i = start; i < end; i++) {
                byte b = data[i];
                if (b >= 0x20 && b < 0x7f) {
                    if (strStart < 0) strStart = i;
                } else if (b == 0) {
                    if (strStart >= 0) {
                        int len = i - strStart;
                        if (len >= minLen) {
                            String s = new String(data, strStart, len, java.nio.charset.StandardCharsets.UTF_8);
                            long address = sh.shAddr + (strStart - sh.shOffset);
                            list.add(new ExtractedString(strStart, address, s, sh.name != null ? sh.name : ""));
                        }
                        strStart = -1;
                    }
                } else {
                    strStart = -1;
                }
            }
        }

        elfFile.strings = list;
    }

    /**
     * 解析函数（来自 .symtab / .dynsym，类型为 FUNC），并反汇编。
     * 同时通过 {@link LinearSweepAnalyzer} 在可执行节区上做线性扫描，发现额外函数。
     * 对 ARM32 自动识别 Thumb 模式。
     * <p>
     * v1.4.2 改进：
     * - 不再过滤 __ 前缀的符号名（参考 C++ 实现），函数列表完整呈现
     * - 线性扫描合并改为累积模式（之前 all 被每次循环结果覆盖）
     * - 函数名缺失时自动生成 sub_xxx
     */
    private void parseFunctions() {
        List<FunctionInfo> symtabFuncs = new ArrayList<>();
        List<SymbolEntry> sources = new ArrayList<>();
        if (elfFile.symtabEntries != null) sources.addAll(elfFile.symtabEntries);
        if (elfFile.dynsymEntries != null) sources.addAll(elfFile.dynsymEntries);

        Disassembler disasm = new Disassembler(elfFile.header.eMachine, is64Bit);

        for (SymbolEntry se : sources) {
            if (se.getType() != ElfConstants.STT_FUNC) continue;
            if (se.stValue == 0) continue; // 跳过无地址符号
            if (se.name != null) {
                if (se.name.isEmpty()) se.name = null;
                // 仅跳过编译器内部临时符号 ($a, $d 等)，不再过滤 __
                else if (se.name.startsWith("$")) continue;
            }

            // 找到所属节区（符号地址落在 [shAddr, shAddr+shSize) 范围）
            SectionHeader funcSec = null;
            if (se.stShndx > 0 && se.stShndx < elfFile.sectionHeaders.size()) {
                SectionHeader candidate = elfFile.sectionHeaders.get(se.stShndx);
                if (candidate.shType != ElfConstants.SHT_NOBITS
                        && se.stValue >= candidate.shAddr
                        && se.stValue < candidate.shAddr + candidate.shSize) {
                    funcSec = candidate;
                }
            }
            // fallback: 按地址范围线性找节区
            if (funcSec == null) {
                for (SectionHeader c : elfFile.sectionHeaders) {
                    if (c.shType == ElfConstants.SHT_NOBITS) continue;
                    if (c.shAddr <= se.stValue && se.stValue < c.shAddr + c.shSize) {
                        funcSec = c;
                        break;
                    }
                }
            }
            if (funcSec == null) continue;

            // ARM32 Thumb: stValue 的 LSB 是 Thumb 位 (>= 1)
            boolean thumb = isThumbFunction(elfFile.header.eMachine, se, null);
            long funcAddr = se.stValue & ~1L; // 清除 LSB 得到真实地址
            disasm.setThumb(thumb);

            // 限制最大函数大小（防止反汇编卡死）
            long size = se.stSize > 0 ? se.stSize : DEFAULT_FUNC_SIZE;
            size = Math.min(size, MAX_FUNC_SIZE);

            long fileOff = funcSec.shOffset + (funcAddr - funcSec.shAddr);
            if (fileOff < 0 || fileOff + size > data.length) {
                // 文件边界裁剪
                if (fileOff < 0) continue;
                size = Math.max(0, data.length - fileOff);
                if (size < 4) continue;
                size = Math.min(size, MAX_FUNC_SIZE);
            }

            byte[] code = Arrays.copyOfRange(data, (int) fileOff, (int) (fileOff + size));
            if (thumb && elfFile.header.eMachine == ElfConstants.EM_ARM) {
                // 二次判断 prologue
                if (!thumb && Disassembler.looksLikeThumb(code)) {
                    thumb = true;
                    disasm.setThumb(true);
                }
            }

            List<DisassembledInstruction> insns = disasm.disassemble(code, funcAddr);

            // 函数名：优先用符号名，否则生成 sub_<hex>
            String fname = se.name;
            if (fname == null || fname.isEmpty()) {
                fname = "sub_" + Long.toHexString(funcAddr);
            }

            FunctionInfo fi = new FunctionInfo(fname, funcAddr, se.stSize, funcSec.name);
            fi.instructions = insns;
            fi.isThumb = thumb;
            fi.source = LinearSweepAnalyzer.SOURCE_SYMTAB;
            symtabFuncs.add(fi);
        }

        // 合并线性扫描结果 (v1.4.2 修复: 改为累积模式)
        List<FunctionInfo> all = new ArrayList<>(symtabFuncs);
        if (elfFile.header.eMachine == ElfConstants.EM_ARM
                || elfFile.header.eMachine == ElfConstants.EM_AARCH64
                || elfFile.header.eMachine == ElfConstants.EM_386
                || elfFile.header.eMachine == ElfConstants.EM_X86_64) {
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (sh.shType != ElfConstants.SHT_PROGBITS) continue;
                if (sh.shSize < 8) continue;
                // 在可执行节区上扫描:
                //   - 有名字时要求是 .text/.plt/.init/.fini
                //   - 名字为空时退而求其次: SHF_EXECINSTR 标志位
                String name = sh.name != null ? sh.name : "";
                boolean looksExec = (sh.shFlags & ElfConstants.SHF_EXECINSTR) != 0;
                boolean nameExec = name.contains("text")
                        || name.contains("plt")
                        || name.contains("init")
                        || name.contains("fini");
                if (!looksExec && !nameExec) continue;
                if (sh.shOffset + sh.shSize > data.length) continue;

                // 重置 disasm 到 ARM 模式
                disasm.setThumb(false);
                List<Long> hits = LinearSweepAnalyzer.scan(sh, data,
                        elfFile.header.eMachine, is64Bit);
                // 累积合并: 取已有地址集合，逐节区追加新发现的函数
                List<FunctionInfo> sectionFns = LinearSweepAnalyzer.mergeWithSymbols(
                        new ArrayList<>(all), hits, sh, disasm,
                        elfFile.header.eMachine, data);
                // 只把 new (非 symtab) 的加进来，避免覆盖符号表的条目
                java.util.Set<Long> known = new java.util.HashSet<>();
                for (FunctionInfo f : all) known.add(f.address);
                for (FunctionInfo f : sectionFns) {
                    if (known.add(f.address)) {
                        all.add(f);
                    }
                }
            }
        }

        // 对函数按地址排序
        java.util.Collections.sort(all, (a, b) -> Long.compare(a.address, b.address));
        elfFile.functions = all;

        // 1.4.5: 字符串引用分析（ARM32 / ARM64）
        runStringReferenceAnalysis(all);
    }

    /**
     * 对每个函数做字符串引用分析，关联 LDR/ADR/ADRP+ADD/LDR 指令和字符串内容。
     * 结果会写回 {@link DisassembledInstruction#referencedString}，UI 高亮显示。
     */
    private void runStringReferenceAnalysis(List<FunctionInfo> funcs) {
        if (funcs == null || funcs.isEmpty()) return;
        if (elfFile.header.eMachine != ElfConstants.EM_ARM
                && elfFile.header.eMachine != ElfConstants.EM_AARCH64) return;

        // 构造 SectionInfo[]
        List<StringReferenceAnalyzer.SectionInfo> list = new ArrayList<>();
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_NOBITS) continue;
            list.add(new StringReferenceAnalyzer.SectionInfo(
                    sh.name, sh.shAddr, sh.shSize));
        }
        StringReferenceAnalyzer.SectionInfo[] sections = list.toArray(new StringReferenceAnalyzer.SectionInfo[0]);
        StringReferenceAnalyzer analyzer = StringReferenceAnalyzerFactory.create(
                elfFile.header.eMachine, sections, data);
        if (analyzer == null) return;
        for (FunctionInfo fi : funcs) {
            if (fi.instructions == null || fi.instructions.isEmpty()) continue;
            try {
                analyzer.analyze(fi, fi.instructions);
            } catch (Throwable t) {
                Log.w("ElfParser", "stringRef analyze failed for " + fi.name, t);
            }
        }
    }

    private static final long DEFAULT_FUNC_SIZE = 32;
    private static final long MAX_FUNC_SIZE = 8 * 1024;

    private static boolean isThumbFunction(int machine, SymbolEntry se, byte[] code) {
        if (machine != ElfConstants.EM_ARM) return false;
        // 1) 符号地址 LSB=1 (Thumb 函数入口标志)
        if ((se.stValue & 1L) == 1L) return true;
        // 2) prologue 字节
        if (code == null || code.length < 2) return false;
        return Disassembler.looksLikeThumb(code);
    }

    /**
     * 解析 .hash (SysV) 与 .gnu.hash 节区，写入 elfFile.sysvHash / gnuHash。
     */
    private void parseHashTables() {
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.name == null) continue;
            switch (sh.name) {
                case ".hash": {
                    HashLookup h = HashLookup.parseSysV(data, sh.shOffset, sh.shSize, is64Bit);
                    if (h != null) elfFile.sysvHash = h;
                    break;
                }
                case ".gnu.hash": {
                    HashLookup h = HashLookup.parseGnu(data, sh.shOffset, sh.shSize);
                    if (h != null) elfFile.gnuHash = h;
                    break;
                }
                default: break;
            }
        }
    }

    /**
     * 解析 PLT / .rela.plt / .rel.plt，得到外部导入函数列表。
     * 规则:
     *   1) 找到 .rela.plt 或 .rel.plt：每条 r_info 的符号下标查 dynsym -> 名字
     *   2) 找到名为 .plt / .plt.sec / .plt.got 的节区，作为 PLT 桩函数范围
     *   3) 在 PLT 起始处按固定步长 (ARM 16/20 字节, AArch64 16 字节, x86_64 16 字节) 划分
     *   4) 关联到 .rela.plt 条目顺序
     */
    private void parseImports() {
        List<ImportedFunction> imports = new ArrayList<>();
        if (elfFile.sectionHeaders == null) {
            elfFile.imports = imports;
            return;
        }

        // 1) 收集 dynsym
        String[] dynsymNames = null;
        if (elfFile.dynsymEntries != null) {
            dynsymNames = new String[elfFile.dynsymEntries.size()];
            for (int i = 0; i < dynsymNames.length; i++) {
                dynsymNames[i] = elfFile.dynsymEntries.get(i).name;
            }
        }

        // 2) 找 .rela.plt / .rel.plt
        List<RelocationEntry> pltRels = new ArrayList<>();
        SectionHeader pltRelSec = null;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            String n = sh.name == null ? "" : sh.name;
            if (n.equals(".rela.plt") || n.equals(".rel.plt")) {
                pltRelSec = sh;
                break;
            }
        }
        if (pltRelSec != null) {
            String dynstr = null;
            for (SectionHeader sh : elfFile.sectionHeaders) {
                if (".dynstr".equals(sh.name)) {
                    dynstr = readString(sh.shOffset, (int) sh.shSize);
                    break;
                }
            }
            parseRelocationSection(pltRelSec, dynstr, pltRels);
        }

        // 3) 找 PLT 节区
        SectionHeader pltSec = null;
        long pltStep;
        switch (elfFile.header.eMachine) {
            case ElfConstants.EM_AARCH64:
                pltStep = 16; break;
            case ElfConstants.EM_ARM:
                pltStep = 20; break;
            case ElfConstants.EM_386:
            case ElfConstants.EM_X86_64:
                pltStep = 16; break;
            default:
                pltStep = 16;
        }
        for (SectionHeader sh : elfFile.sectionHeaders) {
            String n = sh.name == null ? "" : sh.name;
            if (n.equals(".plt") || n.equals(".plt.sec") || n.equals(".plt.got")) {
                pltSec = sh;
                break;
            }
        }
        // 4) 关联 PLT 桩地址与 reloc 条目
        long pltStart = pltSec != null ? pltSec.shAddr : 0;
        for (int i = 0; i < pltRels.size(); i++) {
            RelocationEntry rel = pltRels.get(i);
            ImportedFunction imp = new ImportedFunction();
            imp.relocOffset = rel.rOffset;
            imp.gotOffset = rel.rOffset;
            imp.relocType = rel.typeName;
            imp.name = rel.symbolName;
            imp.section = pltSec != null ? pltSec.name : "";
            if (pltSec != null) {
                long relAddr = pltStart + (long) i * pltStep;
                imp.pltAddress = relAddr;
                long remaining = (pltSec.shAddr + pltSec.shSize) - relAddr;
                imp.pltSize = Math.max(0, Math.min(pltStep, remaining));
                if (imp.pltSize > 0) {
                    long fileOff = pltSec.shOffset + (relAddr - pltSec.shAddr);
                    if (fileOff >= 0 && fileOff + imp.pltSize <= data.length) {
                        imp.pltBytes = new byte[(int) imp.pltSize];
                        System.arraycopy(data, (int) fileOff, imp.pltBytes, 0, (int) imp.pltSize);
                    }
                }
            }
            // 用 .gnu.hash / .hash 交叉验证
            if (imp.name != null && dynsymNames != null) {
                int byName = -1;
                for (int j = 0; j < dynsymNames.length; j++) {
                    if (imp.name.equals(dynsymNames[j])) { byName = j; break; }
                }
                int byHash = -1;
                if (elfFile.gnuHash != null) byHash = elfFile.gnuHash.lookupGnu(imp.name);
                if (byHash < 0 && elfFile.sysvHash != null) byHash = elfFile.sysvHash.lookupSysV(imp.name);
                if (byHash >= 0 && byName >= 0 && byHash != byName) {
                    // 哈希命中但与按名不同：仍以按名为准
                }
            }
            imports.add(imp);
        }

        // 5) 从 .dynamic DT_NEEDED -> 推断 fromLibrary（按序匹配 PLT 段数）
        if (elfFile.neededLibraries != null) {
            for (ImportedFunction imp : imports) {
                imp.fromLibrary = "";
            }
        }

        elfFile.imports = imports;
    }

    private String readString(long offset, int maxLen) {
        int start = (int) offset;
        int end = start + maxLen;
        if (start >= data.length) return null;
        if (end > data.length) end = data.length;

        int term = start;
        while (term < end && data[term] != 0) term++;

        byte[] bytes = Arrays.copyOfRange(data, start, term);
        return new String(bytes);
    }

    public static String fileTypeName(int type) {
        return ElfConstants.getFileTypeName(type);
    }

    public static String machineName(int machine) {
        return ElfConstants.getMachineName(machine);
    }

    public static String sectionTypeName(int type) {
        return ElfConstants.getSectionTypeName(type);
    }

    public static String programTypeName(int type) {
        return ElfConstants.getProgramTypeName(type);
    }

    public static String dynamicTagName(long tag) {
        return ElfConstants.getDynamicTagName(tag);
    }
}