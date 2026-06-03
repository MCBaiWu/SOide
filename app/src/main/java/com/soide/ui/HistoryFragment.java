package com.soide.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.util.HistoryManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = view.findViewById(R.id.rv_history);
        MaterialTextView tvEmpty = view.findViewById(R.id.tv_empty);
        MaterialButton btnClear = view.findViewById(R.id.btn_clear);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        Runnable refresh = () -> {
            List<HistoryManager.Entry> list = HistoryManager.load(requireContext());
            List<ListCardAdapter.Item> items = new java.util.ArrayList<>();
            for (HistoryManager.Entry e : list) {
                ListCardAdapter.Item it = new ListCardAdapter.Item(
                        R.drawable.ic_file,
                        e.fileName != null ? e.fileName : "(no name)",
                        fmt.format(new Date(e.timestamp)),
                        String.format("size=%d  •  %s", e.size,
                                e.filePath != null ? e.filePath : ""));
                items.add(it);
            }
            ListCardAdapter adapter = new ListCardAdapter(items, item -> {
                // 查找对应 entry 并跳转
                List<HistoryManager.Entry> current = HistoryManager.load(requireContext());
                HistoryManager.Entry match = null;
                for (HistoryManager.Entry e : current) {
                    if (item.title.equals(e.fileName)) { match = e; break; }
                }
                if (match != null && match.filePath != null) {
                    Intent it2 = new Intent(requireContext(), SoDetailActivity.class);
                    it2.putExtra(SoDetailActivity.EXTRA_FILE_PATH, match.filePath);
                    it2.putExtra(SoDetailActivity.EXTRA_FILE_NAME, match.fileName);
                    startActivity(it2);
                } else {
                    Toast.makeText(requireContext(), "无缓存", Toast.LENGTH_SHORT).show();
                }
            });
            rv.setAdapter(adapter);
            tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        };
        refresh.run();

        btnClear.setOnClickListener(v -> {
            HistoryManager.clear(requireContext());
            refresh.run();
            Toast.makeText(requireContext(), "已清空", Toast.LENGTH_SHORT).show();
        });
    }
}
