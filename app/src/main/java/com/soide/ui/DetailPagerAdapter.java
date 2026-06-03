package com.soide.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.soide.R;
import com.soide.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * SO 详情 ViewPager 适配器，按 v1.1.0 风格的 tab 顺序：
 *   节区 / 段 / 符号 / 动态 / 重定位 / 字符串 / 函数
 */
public class DetailPagerAdapter extends FragmentStateAdapter {

    private final Context context;
    private final ElfFile elf;
    private final List<String> titles = new ArrayList<>();

    public DetailPagerAdapter(@NonNull FragmentActivity fa, ElfFile elf) {
        super(fa);
        this.context = fa;
        this.elf = elf;
        buildTitles();
    }

    private void buildTitles() {
        titles.clear();
        if (elf.sectionHeaders != null && !elf.sectionHeaders.isEmpty()) {
            titles.add(context.getString(R.string.tab_sections));
        }
        if (elf.programHeaders != null && !elf.programHeaders.isEmpty()) {
            titles.add(context.getString(R.string.tab_programs));
        }
        if ((elf.symtabEntries != null && !elf.symtabEntries.isEmpty())
                || (elf.dynsymEntries != null && !elf.dynsymEntries.isEmpty())) {
            titles.add(context.getString(R.string.tab_symbols));
        }
        if (elf.dynamicEntries != null && !elf.dynamicEntries.isEmpty()) {
            titles.add(context.getString(R.string.tab_dynamic));
        }
        if (elf.relocations != null && !elf.relocations.isEmpty()) {
            titles.add(context.getString(R.string.tab_relocations));
        }
        if (elf.strings != null && !elf.strings.isEmpty()) {
            titles.add(context.getString(R.string.tab_strings));
        }
        if (elf.functions != null && !elf.functions.isEmpty()) {
            titles.add(context.getString(R.string.tab_functions));
        }
        // 最后兜底：ELF 头
        titles.add(context.getString(R.string.tab_header));
    }

    public String getTabTitle(int position) {
        return titles.get(position);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        String title = titles.get(position);
        if (title.equals(context.getString(R.string.tab_sections))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_SECTION, elf);
        }
        if (title.equals(context.getString(R.string.tab_programs))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_PROGRAM, elf);
        }
        if (title.equals(context.getString(R.string.tab_symbols))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_SYMBOL, elf);
        }
        if (title.equals(context.getString(R.string.tab_dynamic))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_DYNAMIC, elf);
        }
        if (title.equals(context.getString(R.string.tab_relocations))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_RELOCATION, elf);
        }
        if (title.equals(context.getString(R.string.tab_strings))) {
            return DetailListTabFragment.newInstance(DetailListTabFragment.KIND_STRING, elf);
        }
        if (title.equals(context.getString(R.string.tab_functions))) {
            return FuncListTabFragment.newInstance(elf);
        }
        // ELF 头
        return HeaderTabFragment.newInstance(elf);
    }

    @Override
    public int getItemCount() {
        return titles.size();
    }
}
