package com.soide.ui;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.ControlFlowAnalyzer;

import java.util.List;
import java.util.Locale;

/**
 * 函数详情 - 控制流 tab
 */
public class ControlFlowTabFragment extends Fragment {

    private static final String ARG_KEY = "key";

    public static ControlFlowTabFragment newInstance(FuncDetailData d) {
        ControlFlowTabFragment f = new ControlFlowTabFragment();
        Bundle b = new Bundle();
        b.putString(ARG_KEY, FuncDetailHost.put(d));
        f.setArguments(b);
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
        TextView tvCount = view.findViewById(R.id.tv_count);
        RecyclerView rv = view.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 隐藏搜索栏 (控制流没必要搜索)
        View til = view.findViewById(R.id.til_search);
        if (til != null) til.setVisibility(View.GONE);

        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_KEY) : null);
        if (d == null || d.function == null) {
            tvCount.setText("控制流");
            return;
        }
        ControlFlowAnalyzer.CFG cfg = ControlFlowAnalyzer.build(d.function.instructions);
        tvCount.setText("控制流  ·  " + cfg.blocks.size() + " 个基本块");
        rv.setAdapter(new CfgAdapter(cfg.blocks));
    }

    /** 把 BB 列表展成可视化的伪图形 */
    static class CfgAdapter extends RecyclerView.Adapter<CfgAdapter.VH> {
        private final List<ControlFlowAnalyzer.Block> blocks;
        CfgAdapter(List<ControlFlowAnalyzer.Block> b) { this.blocks = b; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(12);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(32, 24, 32, 24);
            tv.setTextIsSelectable(true);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            ControlFlowAnalyzer.Block b = blocks.get(i);
            StringBuilder sb = new StringBuilder();
            String tag = b.isEntry ? "[ENTRY] " : (b.isExit ? "[EXIT]  " : "[BB]    ");
            sb.append(tag).append(String.format("0x%x", b.startAddr));
            if (b.endAddr != b.startAddr) {
                sb.append(" - 0x").append(Long.toHexString(b.endAddr));
            }
            sb.append("\n");
            for (int k = 0; k < b.instructions.size(); k++) {
                var ins = b.instructions.get(k);
                String prefix = (k == b.instructions.size() - 1) ? " └─ " : " ├─ ";
                sb.append(String.format(Locale.US, "%s  0x%x  %-7s  %s%n",
                        prefix, ins.address, ins.mnemonic, ins.opStr));
            }
            if (!b.successors.isEmpty()) {
                sb.append(" → ");
                for (int k = 0; k < b.successors.size(); k++) {
                    if (k > 0) sb.append(", ");
                    sb.append(String.format("0x%x", b.successors.get(k)));
                }
            }
            h.tv.setText(sb);

            // 着色: ENTRY 绿色，EXIT 红色
            int color = 0xFF424242;
            if (b.isEntry) color = 0xFF2E7D32;
            else if (b.isExit) color = 0xFFC62828;
            SpannableString sp = new SpannableString(sb);
            int tagLen = tag.length();
            sp.setSpan(new ForegroundColorSpan(color),
                    0, tagLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    0, tagLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.tv.setText(sp);
            h.tv.setBackgroundColor(ContextCompat.getColor(h.tv.getContext(),
                    b.isEntry ? R.color.md_theme_light_primaryContainer : R.color.md_theme_light_surface));
        }

        @Override
        public int getItemCount() { return blocks.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }
}
