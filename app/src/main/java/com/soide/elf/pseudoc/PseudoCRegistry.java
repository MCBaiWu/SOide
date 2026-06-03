package com.soide.elf.pseudoc;

import java.util.ArrayList;
import java.util.List;

/**
 * 伪 C 实现的注册表。当前只内置一个简易实现，
 * 后续要做更复杂反编译器时只需在这里切换/新增。
 */
public final class PseudoCRegistry {

    private PseudoCRegistry() {}

    private static final List<PseudoCConverter> CONVERTERS = new ArrayList<>();
    static {
        CONVERTERS.add(new SimplePseudoC());
    }

    /**
     * 工厂方法：创建给定实现名字的转换器。
     * 如果 name 为 null/"" 则使用默认第一个。
     */
    public static PseudoCConverter create(String name) {
        if (name == null) return CONVERTERS.get(0);
        for (PseudoCConverter c : CONVERTERS) {
            if (c.name().equals(name)) return c;
        }
        return CONVERTERS.get(0);
    }

    public static List<String> allConverterNames() {
        List<String> names = new ArrayList<>();
        for (PseudoCConverter c : CONVERTERS) names.add(c.name());
        return names;
    }
}
