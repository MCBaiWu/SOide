package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.elf.ElfFile;
import com.soide.elf.FunctionInfo;

/**
 * 函数列表 + 反汇编详情 Fragment
 */
public class FunctionsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_functions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rv_list);
        MaterialTextView tvContent = view.findViewById(R.id.tv_content);
        MaterialTextView tvEmpty = view.findViewById(R.id.tv_empty);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        ElfFile elf = ParseResultHolder.get();
        if (elf == null || elf.functions == null || elf.functions.isEmpty()) {
            tvEmpty.setText("(未发现可反汇编的函数符号)");
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            tvContent.setVisibility(View.GONE);
            return;
        }

        FunctionAdapter adapter = new FunctionAdapter(elf.functions, fi -> {
            tvEmpty.setVisibility(View.GONE);
            tvContent.setVisibility(View.VISIBLE);
            tvContent.setText(formatDisassembly(fi));
        });
        rv.setAdapter(adapter);
    }

    private String formatDisassembly(FunctionInfo fi) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(fi.name).append(" ===\n");
        sb.append(String.format("地址: 0x%x | 大小: %d 字节 | 节区: %s\n\n",
                fi.address, fi.size, fi.sectionName));

        if (fi.instructions == null || fi.instructions.isEmpty()) {
            sb.append("(无指令可显示)\n");
        } else {
            for (com.soide.elf.DisassembledInstruction insn : fi.instructions) {
                sb.append(insn.toString()).append("\n");
            }
        }
        return sb.toString();
    }
}