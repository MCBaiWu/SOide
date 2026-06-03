package com.soide.ui;

import android.content.Intent;
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

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.soide.R;
import com.soide.elf.ElfFile;
import com.soide.elf.FunctionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数 tab：左右切换 "全部" / "符号表" / "线性扫描"。
 * 点击函数项打开 FuncDetailActivity。
 * <p>
 * v1.4.1 增加搜索栏。
 */
public class FuncListTabFragment extends Fragment {

    private static final String[] TAB_LABELS = {"全部", "符号表", "线性扫描"};

    private ElfFile elf;

    private TabLayout tab;
    private RecyclerView rv;
    private TextView tvCount;
    private TextInputEditText etSearch;
    private FuncListAdapter adapter;

    private String currentQuery = "";

    public static FuncListTabFragment newInstance(ElfFile elf) {
        FuncListTabFragment f = new FuncListTabFragment();
        f.elf = elf;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_func_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tab = view.findViewById(R.id.sub_tab);
        rv = view.findViewById(R.id.recycler);
        tvCount = view.findViewById(R.id.tv_count);
        etSearch = view.findViewById(R.id.et_search);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        tab.addTab(tab.newTab().setText(TAB_LABELS[0]));
        tab.addTab(tab.newTab().setText(TAB_LABELS[1]));
        tab.addTab(tab.newTab().setText(TAB_LABELS[2]));

        List<FunctionInfo> funcs = elf.functions != null ? elf.functions : new ArrayList<>();

        adapter = new FuncListAdapter(new ArrayList<>(), this::openDetail);
        rv.setAdapter(adapter);

        Runnable refresh = () -> {
            List<FunctionInfo> filtered = new ArrayList<>();
            int idx = tab.getSelectedTabPosition();
            if (idx <= 0) {
                filtered.addAll(funcs);
            } else if (idx == 1) {
                for (FunctionInfo f : funcs) {
                    if (f.source == com.soide.elf.LinearSweepAnalyzer.SOURCE_SYMTAB) {
                        filtered.add(f);
                    }
                }
            } else {
                for (FunctionInfo f : funcs) {
                    if (f.source == com.soide.elf.LinearSweepAnalyzer.SOURCE_LINEARSWEEP) {
                        filtered.add(f);
                    }
                }
            }
            // 应用搜索过滤
            List<FunctionInfo> searched = filterFuncs(filtered, currentQuery);
            adapter.setData(searched);
            updateCount(funcs.size(), searched.size());
        };
        refresh.run();
        tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { refresh.run(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                currentQuery = s.toString();
                refresh.run();
            }
        });
    }

    private void updateCount(int total, int shown) {
        if (total == shown) {
            tvCount.setText(total + " 个函数");
        } else {
            tvCount.setText(shown + " / " + total + " 个函数");
        }
    }

    private static List<FunctionInfo> filterFuncs(List<FunctionInfo> src, String q) {
        if (q == null || q.trim().isEmpty()) return new ArrayList<>(src);
        String q0 = q.trim().toLowerCase();
        List<FunctionInfo> out = new ArrayList<>();
        for (FunctionInfo f : src) {
            if (f == null) continue;
            if (matches(f, q0)) out.add(f);
        }
        return out;
    }

    private static boolean matches(FunctionInfo f, String q) {
        if (f.name != null && f.name.toLowerCase().contains(q)) return true;
        if (f.sectionName != null && f.sectionName.toLowerCase().contains(q)) return true;
        String hex = "0x" + Long.toHexString(f.address);
        if (hex.toLowerCase().contains(q)) return true;
        if (Long.toString(f.address).contains(q)) return true;
        if (q.equals("thumb") && f.isThumb) return true;
        return false;
    }

    private void openDetail(FunctionInfo f) {
        // 序列化函数信息：使用 cacheDir 中的临时 json
        try {
            com.google.gson.Gson g = new com.google.gson.GsonBuilder().serializeNulls().create();
            String json = g.toJson(f);
            java.io.File tmp = new java.io.File(requireContext().getCacheDir(),
                    "func_" + Long.toHexString(f.address) + ".json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                fos.write(json.getBytes("UTF-8"));
            }
            Intent it = new Intent(requireContext(), FuncDetailActivity.class);
            it.putExtra(FuncDetailActivity.EXTRA_JSON_PATH, tmp.getAbsolutePath());
            startActivity(it);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(),
                    "打开失败: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
