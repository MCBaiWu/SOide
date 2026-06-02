package com.soide.elf;

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

        String interp = null;

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

            // 读取 INTERP 段内容（解释器路径）
            if (ph.pType == ElfConstants.PT_INTERP && ph.pFilesz > 0) {
                int len = (int) Math.min(ph.pFilesz, 256);
                interp = new String(data, (int) ph.pOffset, len).trim();
            }

            list.add(ph);
        }

        elfFile.programHeaders = list;
    }

    private void parseSectionHeaders() {
        ElfHeader h = elfFile.header;
        List<SectionHeader> list = new ArrayList<>();

        // 首先读取所有节区头
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

        // 解析节区名字符串表
        String shstrtab = null;
        int shstrndx = h.eShstrndx;
        if (shstrndx > 0 && shstrndx < list.size()) {
            SectionHeader shstrSec = list.get(shstrndx);
            shstrtab = readString(shstrSec.shOffset, (int) shstrSec.shSize);
        }

        // 填回节区名
        for (SectionHeader sh : list) {
            if (shstrtab != null && sh.shName > 0 && sh.shName < shstrtab.length()) {
                int end = shstrtab.indexOf('\0', sh.shName);
                if (end < 0) end = shstrtab.length();
                sh.name = shstrtab.substring(sh.shName, end);
            }
        }

        elfFile.sectionHeaders = list;

        // 解析符号表和动态段
        parseSymbolTables();
        parseDynamic();
    }

    private void parseSymbolTables() {
        Map<Long, String> dynstrMap = new HashMap<>();
        String dynstr = null;

        // 先找出 .dynstr
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (".dynstr".equals(sh.name)) {
                dynstr = readString(sh.shOffset, (int) sh.shSize);
                break;
            }
        }

        // 解析各节区
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_SYMTAB) {
                elfFile.symtabEntries = parseSymbols(sh, null);
            } else if (sh.shType == ElfConstants.SHT_DYNSYM) {
                elfFile.dynsymEntries = parseSymbols(sh, dynstr);
            }
        }
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

            // 从字符串表获取符号名
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
        // 找到 .dynamic 节区
        SectionHeader dynamicSec = null;
        for (SectionHeader sh : elfFile.sectionHeaders) {
            if (sh.shType == ElfConstants.SHT_DYNAMIC) {
                dynamicSec = sh;
                break;
            }
        }

        // 也检查程序头中的 DYNAMIC 段
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

        // 找到 .dynstr
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

            // 记录 STRTAB 基址，用于后续名字解析
            if (de.dTag == ElfConstants.DT_STRTAB) {
                dynstrBase = de.dVal;
            }

            entries.add(de);
        }

        // 解析动态字符串（NEEDED, SONAME 等）
        if (dynstrBase != 0) {
            for (DynamicEntry de : entries) {
                long strOff = 0;
                if (de.dTag == ElfConstants.DT_NEEDED || de.dTag == ElfConstants.DT_SONAME) {
                    long actualOffset = de.dVal;

                    // 尝试通过偏移直接读 .dynstr
                    if (dynstr != null) {
                        // dVal 是相对于文件加载基址 - 需要用 .dynstr 的 addr 来修正
                        // 简单场景：dVal 就是字符串在 .dynstr 节中的偏移
                        long stroff = actualOffset;
                        // 如果 dVal 是虚拟地址，尝试减去 dynstr 的虚拟基址
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

    private String readString(long offset, int maxLen) {
        int start = (int) offset;
        int end = start + maxLen;
        if (start >= data.length) return null;
        if (end > data.length) end = data.length;

        // 找第一个 \0 作为字符串终止
        int term = start;
        while (term < end && data[term] != 0) term++;

        byte[] bytes = Arrays.copyOfRange(data, start, term);
        return new String(bytes);
    }
}