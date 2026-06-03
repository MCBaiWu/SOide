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
import com.soide.elf.ElfConstants;
import com.soide.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * ELF 头 tab：显示关键元数据项。
 * v1.4.1 增加搜索栏。
 */
public class HeaderTabFragment extends Fragment {

    private ElfFile elf;
    private RecyclerView rv;
    private TextView tvCount;
    private TextInputEditText etSearch;
    private DetailAdapter adapter;
    private List<DetailAdapter.Item> allItems = new ArrayList<>();
    private String currentQuery = "";

    public static HeaderTabFragment newInstance(ElfFile elf) {
        HeaderTabFragment f = new HeaderTabFragment();
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
        tvCount = view.findViewById(R.id.tv_count);
        rv = view.findViewById(R.id.recycler);
        etSearch = view.findViewById(R.id.et_search);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        allItems = build();
        adapter = new DetailAdapter(filter(allItems, currentQuery), i -> {});
        rv.setAdapter(adapter);
        updateCount();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                currentQuery = s.toString();
                adapter = new DetailAdapter(filter(allItems, currentQuery), i -> {});
                rv.setAdapter(adapter);
                updateCount();
            }
        });
    }

    private void updateCount() {
        if (allItems.size() == filter(allItems, currentQuery).size()) {
            tvCount.setText("ELF 文件头");
        } else {
            tvCount.setText("ELF 文件头  ·  " + filter(allItems, currentQuery).size()
                    + " / " + allItems.size());
        }
    }

    private static List<DetailAdapter.Item> filter(List<DetailAdapter.Item> src, String q) {
        if (q == null || q.trim().isEmpty()) return new ArrayList<>(src);
        String q0 = q.trim().toLowerCase();
        List<DetailAdapter.Item> out = new ArrayList<>();
        for (DetailAdapter.Item it : src) {
            if (it == null) continue;
            if (contains(it.title, q0) || contains(it.subtitle, q0) || contains(it.type, q0)) {
                out.add(it);
            }
        }
        return out;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private List<DetailAdapter.Item> build() {
        List<DetailAdapter.Item> list = new ArrayList<>();
        if (elf == null || elf.header == null) return list;
        com.soide.elf.ElfHeader h = elf.header;
        add(list, "Magic", "7f 45 4c 46");
        add(list, "Class (位数)", h.eiClass == ElfConstants.ELFCLASS64 ? "ELF64" : "ELF32");
        add(list, "Data (编码)", h.eiData == ElfConstants.ELFDATA2LSB ? "Little Endian" : "Big Endian");
        add(list, "OS/ABI", "0x" + Integer.toHexString(h.eiOsabi));
        add(list, "Type", ElfConstants.getFileTypeName(h.eType));
        add(list, "Machine", ElfConstants.getMachineName(h.eMachine));
        add(list, "Version", String.valueOf(h.eVersion));
        add(list, "Entry", "0x" + Long.toHexString(h.eEntry));
        add(list, "ProgramHdr Offset", "0x" + Long.toHexString(h.ePhoff));
        add(list, "SectionHdr Offset", "0x" + Long.toHexString(h.eShoff));
        add(list, "Flags", "0x" + Integer.toHexString(h.eFlags));
        add(list, "ELF Header Size", String.valueOf(h.eEhsize));
        add(list, "ProgramHdr Entry Size", String.valueOf(h.ePhentsize));
        add(list, "ProgramHdr Count", String.valueOf(h.ePhnum));
        add(list, "SectionHdr Entry Size", String.valueOf(h.eShentsize));
        add(list, "SectionHdr Count", String.valueOf(h.eShnum));
        add(list, "shstrndx", String.valueOf(h.eShstrndx));
        if (elf.neededLibraries != null && !elf.neededLibraries.isEmpty()) {
            add(list, "Needed Libraries (" + elf.neededLibraries.size() + ")",
                    String.join(", ", elf.neededLibraries));
        }
        return list;
    }

    private void add(List<DetailAdapter.Item> list, String title, String value) {
        DetailAdapter.Item it = new DetailAdapter.Item();
        it.type = "ELF";
        it.title = title;
        it.subtitle = value;
        it.meta = "";
        list.add(it);
    }
}
