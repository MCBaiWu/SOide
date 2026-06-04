package com.soide.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.soide.R;
import com.soide.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用列表 tab：渲染一种类型的所有条目。
 * 通过 kind 区分数据来源。
 * <p>
 * v1.4.1 增加搜索栏：按 title / subtitle / type / meta 关键字过滤。
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
    public static final int KIND_IMPORT = 8;
    public static final int KIND_LIBRARY = 9;
    public static final int KIND_HASH = 10;

    private static final String ARG_KIND = "kind";
    private static final String ARG_ELF_PATH = "elf_path";

    private int kind;
    private ElfFile elf;
    private RecyclerView rv;
    private TextView tvCount;
    private DetailAdapter adapter;
    private TextInputEditText etSearch;
    private List<DetailAdapter.Item> allItems = new ArrayList<>();
    private String currentQuery = "";

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
        etSearch = view.findViewById(R.id.et_search);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        allItems = build();
        adapter = new DetailAdapter(filter(allItems, currentQuery), this::onItemClick);
        rv.setAdapter(adapter);
        updateCount(allItems.size(), filter(allItems, currentQuery).size());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                currentQuery = s.toString();
                List<DetailAdapter.Item> filtered = filter(allItems, currentQuery);
                adapter = new DetailAdapter(filtered, DetailListTabFragment.this::onItemClick);
                rv.setAdapter(adapter);
                updateCount(allItems.size(), filtered.size());
            }
        });
    }

    private void updateCount(int total, int shown) {
        if (total == shown) {
            tvCount.setText(total + " 项");
        } else {
            tvCount.setText(shown + " / " + total + " 项");
        }
    }

    private void onItemClick(DetailAdapter.Item item) {
        // TODO: 点击符号或函数可跳到详情
    }

    /**
     * 大小写不敏感的多字段模糊匹配。
     * 支持 "0x" / 十六进制地址识别: 用户输入 0x1234 时匹配所有 "0x1234" 子串。
     */
    private static List<DetailAdapter.Item> filter(List<DetailAdapter.Item> src, String q) {
        if (q == null || q.trim().isEmpty()) return new ArrayList<>(src);
        String q0 = q.trim().toLowerCase();
        List<DetailAdapter.Item> out = new ArrayList<>();
        for (DetailAdapter.Item it : src) {
            if (matches(it, q0)) out.add(it);
        }
        return out;
    }

    private static boolean matches(DetailAdapter.Item it, String q) {
        if (it == null) return false;
        if (contains(it.title, q) || contains(it.subtitle, q) || contains(it.type, q) || contains(it.meta, q)) {
            return true;
        }
        return false;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private List<DetailAdapter.Item> build() {
        List<DetailAdapter.Item> list = new ArrayList<>();
        if (elf == null) return list;
        switch (kind) {
            case KIND_SECTION:
                if (elf.sectionHeaders != null) {
                    int idx = 0;
                    for (com.soide.elf.SectionHeader s : elf.sectionHeaders) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        // v1.4.6: 节区标签页不再显示节区名，改为显示编号 + 类型
                        it.type = "节区 #" + idx;
                        String typeName = com.soide.elf.ElfParser.sectionTypeName(s.shType);
                        it.title = typeName; // e.g. SHT_PROGBITS
                        it.subtitle = "size=" + s.shSize + "  flags=0x" + Long.toHexString(s.shFlags);
                        it.meta = String.format("addr=0x%x  off=0x%x  link=%d",
                                s.shAddr, s.shOffset, s.shLink);
                        it.copyText = String.format("%s  #%d  %s  addr=0x%x  off=0x%x  size=%d  flags=0x%x",
                                typeName, idx,
                                s.name != null ? s.name : "(unnamed)",
                                s.shAddr, s.shOffset, s.shSize, s.shFlags);
                        list.add(it);
                        idx++;
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
            case KIND_IMPORT:
                if (elf.imports != null) {
                    for (com.soide.elf.ImportedFunction imp : elf.imports) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = imp.relocType != null ? imp.relocType : "IMPORT";
                        it.title = imp.name != null ? imp.name : "(unknown)";
                        if (imp.name != null && imp.name.startsWith("_Z")) {
                            com.soide.util.Demangler.Result dm = com.soide.util.Demangler.demangle(imp.name);
                            if (dm.supported) it.subtitle = dm.demangled;
                            else it.subtitle = imp.section + "  PLT 桩函数";
                        } else {
                            it.subtitle = imp.section + "  PLT 桩函数";
                        }
                        it.meta = String.format("plt=0x%x got=0x%x", imp.pltAddress, imp.gotOffset);
                        list.add(it);
                    }
                }
                break;
            case KIND_LIBRARY:
                if (elf.neededLibraries != null) {
                    for (String lib : elf.neededLibraries) {
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = "DT_NEEDED";
                        it.title = lib;
                        it.subtitle = "动态链接依赖";
                        it.meta = "";
                        list.add(it);
                    }
                }
                break;
            case KIND_HASH:
                if (elf.gnuHash != null) {
                    addHash(list, ".gnu.hash", "GNU", elf.gnuHash.nbucket, elf.gnuHash.maskwords,
                            elf.gnuHash.bloomShift, elf.gnuHash.bloom != null ? elf.gnuHash.bloom.length * 8 : 0);
                }
                if (elf.sysvHash != null) {
                    addHash(list, ".hash", "SysV", elf.sysvHash.nbucket, elf.sysvHash.maskwords,
                            0, elf.sysvHash.bloom != null ? elf.sysvHash.bloom.length * 8 : 0);
                }
                if (elf.dynsymEntries != null) {
                    for (int i = 0; i < elf.dynsymEntries.size(); i++) {
                        com.soide.elf.SymbolEntry s = elf.dynsymEntries.get(i);
                        if (s.name == null || s.name.isEmpty()) continue;
                        long gnu = com.soide.elf.HashLookup.gnuHash(s.name);
                        long sysv = com.soide.elf.HashLookup.sysvHash(s.name);
                        int gnuIdx = elf.gnuHash != null ? elf.gnuHash.lookupGnu(s.name) : -1;
                        int sysvIdx = elf.sysvHash != null ? elf.sysvHash.lookupSysV(s.name) : -1;
                        boolean ok = (gnuIdx == i || gnuIdx < 0)
                                && (sysvIdx == i || sysvIdx < 0);
                        DetailAdapter.Item it = new DetailAdapter.Item();
                        it.type = ok ? "OK" : "MISMATCH";
                        it.title = s.name;
                        it.subtitle = String.format("idx=%d  gnu=0x%x sysv=0x%x", i, gnu, sysv);
                        it.meta = String.format("gnu idx=%d  sysv idx=%d", gnuIdx, sysvIdx);
                        list.add(it);
                    }
                }
                break;
        }
        return list;
    }

    private void addHash(List<DetailAdapter.Item> list, String name, String kind,
                          int nbucket, int maskwords, int shift, int bloomBits) {
        DetailAdapter.Item it = new DetailAdapter.Item();
        it.type = "HASH";
        it.title = name;
        it.subtitle = String.format("%s  nbucket=%d  nchain=%d", kind, nbucket, maskwords);
        it.meta = shift > 0 ? ("bloom=" + bloomBits + " bits, shift=" + shift) : "no bloom";
        list.add(it);
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
