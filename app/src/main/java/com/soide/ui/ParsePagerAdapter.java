package com.soide.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.soide.elf.ElfFile;

/**
 * ViewPager2 适配器：基本 / 段 / 节区 / 符号 / 字符串 / 重定位 / 函数 共 7 个 Tab
 */
public class ParsePagerAdapter extends FragmentStateAdapter {

    private final ElfFile elf;

    public ParsePagerAdapter(@NonNull FragmentActivity activity, ElfFile elf) {
        super(activity);
        this.elf = elf;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return TextContentFragment.newInstance(Formatter.formatHeader(elf));
            case 1: return TextContentFragment.newInstance(Formatter.formatProgramHeaders(elf));
            case 2: return TextContentFragment.newInstance(Formatter.formatSectionHeaders(elf));
            case 3: return TextContentFragment.newInstance(Formatter.formatSymbols(elf));
            case 4: return TextContentFragment.newInstance(Formatter.formatStrings(elf));
            case 5: return TextContentFragment.newInstance(Formatter.formatRelocations(elf));
            case 6: return TextContentFragment.newInstance(Formatter.formatDynamic(elf));
            case 7: return new FunctionsFragment();
            default: return TextContentFragment.newInstance("(无内容)");
        }
    }

    @Override
    public int getItemCount() {
        return 8;
    }

    public String getTabTitle(int position) {
        switch (position) {
            case 0: return "基本";
            case 1: return "段";
            case 2: return "节区";
            case 3: return "符号表";
            case 4: return "字符串";
            case 5: return "重定位";
            case 6: return "动态";
            case 7: return "函数";
            default: return "";
        }
    }
}