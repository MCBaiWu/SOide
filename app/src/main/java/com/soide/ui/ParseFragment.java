package com.soide.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.elf.ElfFile;
import com.soide.elf.ElfParser;
import com.soide.util.CacheStore;
import com.soide.util.HistoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ParseFragment extends Fragment {

    private MaterialTextView tvFilePath;
    private MaterialTextView tvSummary;
    private LinearProgressIndicator progressBar;
    private MaterialButton btnOpen;
    private MaterialButton btnOpenCached;
    private RecyclerView rvItems;

    private String currentFilePath;
    private String currentFileName;
    private long currentFileSize;

    private final List<ListCardAdapter.Item> items = new ArrayList<>();
    private ListCardAdapter adapter;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_parse, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvFilePath = view.findViewById(R.id.tv_file_path);
        tvSummary = view.findViewById(R.id.tv_summary);
        progressBar = view.findViewById(R.id.progress_bar);
        btnOpen = view.findViewById(R.id.btn_open);
        btnOpenCached = view.findViewById(R.id.btn_open_cached);
        rvItems = view.findViewById(R.id.rv_items);

        rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ListCardAdapter(items, this::onItemClick);
        rvItems.setAdapter(adapter);

        btnOpen.setOnClickListener(v -> openPicker());
        btnOpenCached.setOnClickListener(v -> loadFromCache());
    }

    @Override
    public void onResume() {
        super.onResume();
        // 如果已经分析过，恢复时显示摘要
        if (currentFilePath != null) {
            refreshSummaryFromCache();
        }
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        currentFileName = getFileName(uri);
        currentFileSize = querySize(uri);
        tvFilePath.setText(currentFileName != null ? currentFileName : "selected");
        showProgress(true);

        new Thread(() -> {
            File tempFile = null;
            try {
                tempFile = copyToTempFile(uri);
                currentFilePath = tempFile.getAbsolutePath();

                ElfParser parser = new ElfParser();
                ElfFile elfFile = parser.parse(tempFile);

                // 缓存
                CacheStore.save(requireContext(), currentFilePath, elfFile);
                HistoryManager.add(requireContext(), currentFileName, currentFilePath, currentFileSize);

                requireActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.parse_success), Toast.LENGTH_SHORT).show();
                    refreshSummaryFromCache();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.parse_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadFromCache() {
        if (currentFilePath == null) {
            Toast.makeText(requireContext(), "请先选择并解析文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CacheStore.has(requireContext(), currentFilePath)) {
            Toast.makeText(requireContext(), "无缓存，请重新选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        openDetail();
    }

    private void refreshSummaryFromCache() {
        if (currentFilePath == null) return;
        ElfFile elf = CacheStore.load(requireContext(), currentFilePath);
        if (elf == null) {
            tvSummary.setVisibility(View.GONE);
            items.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        tvSummary.setVisibility(View.VISIBLE);
        tvSummary.setText(buildSummary(elf));

        items.clear();
        items.addAll(buildItems(elf));
        adapter.notifyDataSetChanged();
    }

    private String buildSummary(ElfFile elf) {
        StringBuilder sb = new StringBuilder();
        if (elf.header != null) {
            sb.append("架构: ").append(ElfParser.machineName(elf.header.eMachine));
            sb.append("  •  位数: ").append(elf.header.eiClass == 2 ? "64-bit" : "32-bit");
            sb.append("  •  类型: ").append(ElfParser.fileTypeName(elf.header.eType));
            sb.append("\n节区: ").append(elf.sectionHeaders != null ? elf.sectionHeaders.size() : 0);
            sb.append("  •  段: ").append(elf.programHeaders != null ? elf.programHeaders.size() : 0);
            sb.append("  •  符号: ").append(countSymbols(elf));
            sb.append("  •  字符串: ").append(elf.strings != null ? elf.strings.size() : 0);
            sb.append("  •  函数: ").append(elf.functions != null ? elf.functions.size() : 0);
            sb.append("  •  重定位: ").append(elf.relocations != null ? elf.relocations.size() : 0);
            sb.append("  •  导入: ").append(elf.imports != null ? elf.imports.size() : 0);
            sb.append("  •  依赖: ").append(elf.neededLibraries != null ? elf.neededLibraries.size() : 0);
        }
        return sb.toString();
    }

    private int countSymbols(ElfFile elf) {
        int n = 0;
        if (elf.symtabEntries != null) n += elf.symtabEntries.size();
        if (elf.dynsymEntries != null) n += elf.dynsymEntries.size();
        return n;
    }

    private List<ListCardAdapter.Item> buildItems(ElfFile elf) {
        List<ListCardAdapter.Item> list = new ArrayList<>();
        if (elf.header != null) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_file, "ELF 头",
                    "类型 / 架构 / 入口 / 标志",
                    "type=" + ElfParser.fileTypeName(elf.header.eType)
                            + ", arch=" + ElfParser.machineName(elf.header.eMachine)));
        }
        if (elf.sectionHeaders != null && !elf.sectionHeaders.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_section, "节区 (" + elf.sectionHeaders.size() + ")",
                    "name / type / addr / size",
                    "PROGBITS, STRTAB, SYMTAB, ...",
                    "section_headers"));
        }
        if (elf.programHeaders != null && !elf.programHeaders.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_program, "段 (" + elf.programHeaders.size() + ")",
                    "type / offset / vaddr / size",
                    "LOAD, DYNAMIC, INTERP, ...",
                    "program_headers"));
        }
        if (elf.symtabEntries != null && !elf.symtabEntries.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_symbol, "符号表 (" + elf.symtabEntries.size() + ")",
                    "bind / type / name / value / size",
                    "全局符号, 函数, 对象, ...",
                    "symtab"));
        }
        if (elf.dynsymEntries != null && !elf.dynsymEntries.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_symbol, "动态符号 (" + elf.dynsymEntries.size() + ")",
                    "动态链接的符号",
                    "GLOBAL / WEAK, ...",
                    "dynsym"));
        }
        if (elf.dynamicEntries != null && !elf.dynamicEntries.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_dynamic, "动态段 (" + elf.dynamicEntries.size() + ")",
                    "DT_NEEDED / DT_SONAME / ...",
                    "动态链接信息",
                    "dynamic"));
        }
        if (elf.relocations != null && !elf.relocations.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_relocation, "重定位 (" + elf.relocations.size() + ")",
                    "type / offset / symbol",
                    "GLOB_DAT, JUMP_SLOT, ...",
                    "relocations"));
        }
        if (elf.strings != null && !elf.strings.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_string, "字符串 (" + elf.strings.size() + ")",
                    "data / offset / address",
                    "可打印字符串",
                    "strings"));
        }
        if (elf.functions != null && !elf.functions.isEmpty()) {
            int thumbCount = 0;
            for (com.soide.elf.FunctionInfo f : elf.functions) {
                if (f.isThumb) thumbCount++;
            }
            String meta = "符号表 + 线性扫描发现";
            if (thumbCount > 0) meta += "  (Thumb: " + thumbCount + ")";
            list.add(new ListCardAdapter.Item(R.drawable.ic_function, "函数 (" + elf.functions.size() + ")",
                    "name / addr / size / instructions",
                    meta,
                    "functions"));
        }
        if (elf.imports != null && !elf.imports.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_import, "导入函数 (" + elf.imports.size() + ")",
                    "PLT 桩函数 / 外部符号",
                    "解析 .plt / .rela.plt",
                    "imports"));
        }
        if (elf.neededLibraries != null && !elf.neededLibraries.isEmpty()) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_library, "依赖库 (" + elf.neededLibraries.size() + ")",
                    "DT_NEEDED 动态链接依赖",
                    String.join(", ", elf.neededLibraries.size() > 4
                            ? new String[]{elf.neededLibraries.get(0), "..."}
                            : new String[]{}),
                    "libraries"));
        }
        if (elf.gnuHash != null || elf.sysvHash != null) {
            list.add(new ListCardAdapter.Item(R.drawable.ic_hash, "哈希表",
                    ".gnu.hash / .hash 交叉验证",
                    "dynsym 符号名 → 索引",
                    "hash"));
        }
        return list;
    }

    private void onItemClick(ListCardAdapter.Item item) {
        if (item.tag == null) {
            // ELF 头：单独展开
            Toast.makeText(requireContext(), "已选择: " + item.title, Toast.LENGTH_SHORT).show();
            return;
        }
        openDetail();
    }

    private void openDetail() {
        Intent it = new Intent(requireContext(), SoDetailActivity.class);
        it.putExtra(SoDetailActivity.EXTRA_FILE_PATH, currentFilePath);
        it.putExtra(SoDetailActivity.EXTRA_FILE_NAME, currentFileName);
        startActivity(it);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnOpen.setEnabled(!show);
        btnOpenCached.setEnabled(!show);
        if (show) progressBar.setIndeterminate(true);
    }

    private String getFileName(Uri uri) {
        String name = null;
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return name != null ? name : uri.getLastPathSegment();
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private File copyToTempFile(Uri uri) throws Exception {
        File tempFile = File.createTempFile("soide_", ".tmp", requireContext().getCacheDir());
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        return tempFile;
    }
}
