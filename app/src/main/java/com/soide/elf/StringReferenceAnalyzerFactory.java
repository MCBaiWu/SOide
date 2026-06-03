package com.soide.elf;

/**
 * 工厂：根据 ELF e_machine 创建合适的字符串引用分析器。
 */
public final class StringReferenceAnalyzerFactory {

    private StringReferenceAnalyzerFactory() {}

    public static StringReferenceAnalyzer create(int machineType,
                                                 StringReferenceAnalyzer.SectionInfo[] sections,
                                                 byte[] fileData) {
        if (fileData == null) return null;
        switch (machineType) {
            case ElfConstants.EM_ARM:
                return new Arm32StringReferenceAnalyzer(sections, fileData, machineType);
            case ElfConstants.EM_AARCH64:
                return new Arm64StringReferenceAnalyzer(sections, fileData, machineType);
            default:
                return null;
        }
    }
}
