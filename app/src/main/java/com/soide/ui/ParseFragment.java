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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.soide.MainActivity;
import com.soide.R;
import com.soide.elf.ElfFile;
import com.soide.elf.ElfParser;
import com.soide.util.CacheStore;
import com.soide.util.HistoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * SO 解析 fragment。
 * <p>
 * v1.4.0 行为：
 * - 选完文件并解析成功后，<b>直接跳转</b>到 {@link SoDetailActivity} 显示函数/节区/符号等。
 * - 主页保留 "历史记录" 按钮，调用 {@link MainActivity#selectTab(int)} 切到历史记录页。
 * - 主页不再显示节区/字符串等概要列表（避免重复点击）。
 */
public class ParseFragment extends Fragment {

    private static final String TAG = "ParseFragment";

    private MaterialTextView tvFilePath;
    private MaterialTextView tvSummary;
    private LinearProgressIndicator progressBar;
    private MaterialButton btnOpen;
    private MaterialButton btnHistory;

    private String currentFilePath;
    private String currentFileName;
    private long currentFileSize;

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
        btnHistory = view.findViewById(R.id.btn_history);

        btnOpen.setOnClickListener(v -> openPicker());
        btnHistory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(MainActivity.TAB_HISTORY);
            }
        });
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

                // 缓存 + 历史
                CacheStore.save(requireContext(), currentFilePath, elfFile);
                HistoryManager.add(requireContext(), currentFileName, currentFilePath, currentFileSize);

                requireActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.parse_success), Toast.LENGTH_SHORT).show();
                    // 解析成功 → 直接跳到 SO 详情
                    openDetail();
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

    private void refreshSummaryFromCache() {
        if (currentFilePath == null) return;
        ElfFile elf = CacheStore.load(requireContext(), currentFilePath);
        if (elf == null) {
            tvSummary.setVisibility(View.GONE);
            return;
        }
        tvSummary.setVisibility(View.VISIBLE);
        tvSummary.setText(buildSummary(elf));
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

    private void openDetail() {
        Intent it = new Intent(requireContext(), SoDetailActivity.class);
        it.putExtra(SoDetailActivity.EXTRA_FILE_PATH, currentFilePath);
        it.putExtra(SoDetailActivity.EXTRA_FILE_NAME, currentFileName);
        startActivity(it);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnOpen.setEnabled(!show);
        btnHistory.setEnabled(!show);
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
