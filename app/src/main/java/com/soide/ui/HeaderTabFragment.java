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
import com.soide.elf.ElfConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * ELF 头 tab：显示关键元数据项。
 */
public class HeaderTabFragment extends Fragment {

    public static HeaderTabFragment newInstance(ElfFile elf) {
        HeaderTabFragment f = new HeaderTabFragment();
        f.elf = elf;
        return f;
    }

    private ElfFile elf;

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
        TextView tvCount = view.findViewById(R.id.tv_count);
        RecyclerView rv = view.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<DetailAdapter.Item> items = build();
        DetailAdapter adapter = new DetailAdapter(items, i -> {});
        rv.setAdapter(adapter);
        tvCount.setText("ELF 文件头");
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
