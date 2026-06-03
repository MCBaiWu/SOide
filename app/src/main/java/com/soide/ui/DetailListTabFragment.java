package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用列表 tab：渲染一种类型的所有条目。
 * 通过 kind 区分数据来源。
 */
public class DetailListTabFragment extends Fragment {

    public static final int KIND_HEADER = 0;
    public static final int KIND_SECTION = 1;
    public static final int KIND_PROGRAM = 2;
    public static final int KIND_SYMBOL = 3;
    public static final int KIND_DYNAMIC = 4;
    public static final int KIND_RELOCATION = 5;
    public static final int KIND_STRING = 6;
    public static final int KIND_FUNCTION = 7;

    private static final String ARG_KIND = "kind";
    private static final String ARG_ELF_PATH = "elf_path";

    private int kind;
    private ElfFile elf;
    private RecyclerView rv;
    private TextView tvCount;
    private DetailAdapter adapter;

    public static DetailListTabFragment newInstance(int kind, ElfFile elf) {
        DetailListTabFragment f = new DetailListTabFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_KIND, kind);
        // 我们传递 ElfFile 引用（同进程）
        f.setArguments(b);
        f.elf = elf;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            kind = getArguments().getInt(ARG_KIND, KIND_SECTION);
        }
        rv = view.findViewById(R.id.recycler);
        tvCount = view.findViewById(R.id.tv_count);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<DetailAdapter.Item> items = build();
        adapter = new DetailAdapter(items, this::onItemClick);
        rv.setAdapter(adapter);
        tvCount.setText(items.size() + " 项");
    }

    private void onItemClick(DetailAdapter.Item item) {
        // TODO: 点击符号或函数可跳到详情
    }

    private List<DetailAdapter.Item> build() {
        List<DetailAdapter.Item> list = new ArrayList<>();
        if (elf == null) return list;
        switch (kind) {
            case KIND_SECTION:
                if (elf.sectionHeaders != null) {
                    for (com.soide.elf.SectionHeader s : elf.sectionHeaders) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = s.name != null ? s.name : "(unnamed)";
                        it.title = s.name != null ? s.name : "(unnamed)";
                        it.subtitle = com.soide.elf.ElfParser.sectionTypeName(s.shType)
                                + "  size=" + s.shSize;
                        it.meta = String.format("addr=0x%x off=0x%x",
                                s.shAddr, s.shOffset);
                        list.add(it);
                    }
                }
                break;
            case KIND_PROGRAM:
                if (elf.programHeaders != null) {
                    for (com.soide.elf.ProgramHeader p : elf.programHeaders) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = com.soide.elf.ElfParser.programTypeName(p.pType);
                        it.title = com.soide.elf.ElfParser.programTypeName(p.pType);
                        it.subtitle = String.format("vaddr=0x%x memsz=0x%x",
                                p.pVaddr, p.pMemsz);
                        it.meta = String.format("off=0x%x filesz=0x%x flags=0x%x",
                                p.pOffset, p.pFilesz, p.pFlags);
                        list.add(it);
                    }
                }
                break;
            case KIND_SYMBOL: {
                if (elf.symtabEntries != null) {
                    for (com.soide.elf.SymbolEntry s : elf.symtabEntries) {
                        list.add(symbolItem(s, ".symtab"));
                    }
                }
                if (elf.dynsymEntries != null) {
                    for (com.soide.elf.SymbolEntry s : elf.dynsymEntries) {
                        list.add(symbolItem(s, ".dynsym"));
                    }
                }
                break;
            }
            case KIND_DYNAMIC:
                if (elf.dynamicEntries != null) {
                    for (com.soide.elf.DynamicEntry d : elf.dynamicEntries) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = com.soide.elf.ElfParser.dynamicTagName(d.dTag);
                        it.title = com.soide.elf.ElfParser.dynamicTagName(d.dTag);
                        it.subtitle = d.valueName != null ? d.valueName : ("0x" + Long.toHexString(d.dVal));
                        it.meta = "val=0x" + Long.toHexString(d.dVal);
                        list.add(it);
                    }
                }
                break;
            case KIND_RELOCATION:
                if (elf.relocations != null) {
                    for (com.soide.elf.RelocationEntry r : elf.relocations) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = r.typeName;
                        it.title = r.typeName;
                        it.subtitle = r.symbolName != null && !r.symbolName.isEmpty()
                                ? r.symbolName : "(no symbol)";
                        it.meta = String.format("offset=0x%x", r.rOffset);
                        list.add(it);
                    }
                }
                break;
            case KIND_STRING:
                if (elf.strings != null) {
                    for (com.soide.elf.ExtractedString s : elf.strings) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = s.sectionName;
                        it.title = truncate(s.value, 60);
                        it.subtitle = s.sectionName + "  @0x" + Long.toHexString(s.address);
                        it.meta = "off=0x" + Long.toHexString(s.offset);
                        list.add(it);
                    }
                }
                break;
        }
        return list;
    }

    private DetailAdapter.Item symbolItem(com.soide.elf.SymbolEntry s, String src) {
        DetailAdapter.Item it = new DetailAdapter.Item();
        it.type = s.getBindName() + " " + s.getTypeName();
        it.title = s.name != null ? s.name : "(unnamed)";
        if (s.name != null && s.name.startsWith("_Z")) {
            // C++ mangled: 给出 demangled
            com.soide.util.Demangler.Result dm = com.soide.util.Demangler.demangle(s.name);
            if (dm.supported) it.subtitle = dm.demangled;
            else it.subtitle = s.name;
        } else {
            it.subtitle = s.getTypeName() + "  size=" + s.stSize;
        }
        it.meta = String.format("addr=0x%x [%s]", s.stValue, src);
        return it;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n - 3) + "...";
    }
}
