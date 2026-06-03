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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.CrossReferenceAnalyzer;
import com.soide.util.Demangler;
import com.soide.util.ThemeUtils;

import java.util.List;
import java.util.Locale;

/**
 * 函数详情 - 交叉引用 tab
 * v1.4.3 改进：
 * - 颜色用 ThemeUtils 跟随 day/night
 * - 区分引用类型 (call / jump / cond / load) 着色
 * - 拆为 2 行：原始引用 + demangle 提示
 */
public class XRefTabFragment extends Fragment {

    private static final String ARG_KEY = "key";

    public static XRefTabFragment newInstance(FuncDetailData d) {
        XRefTabFragment f = new XRefTabFragment();
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

        View til = view.findViewById(R.id.til_search);
        if (til != null) til.setVisibility(View.GONE);

        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_KEY) : null);
        if (d == null || d.function == null) {
            tvCount.setText("交叉引用");
            return;
        }
        List<CrossReferenceAnalyzer.XRef> refs = CrossReferenceAnalyzer.find(
                d.function.instructions, d.symbols, d.imports);
        tvCount.setText(String.format(Locale.US, "交叉引用  ·  %d 处", refs.size()));
        rv.setAdapter(new XRefAdapter(refs));
    }

    static class XRefAdapter extends RecyclerView.Adapter<XRefAdapter.VH> {
        private final List<CrossReferenceAnalyzer.XRef> refs;
        XRefAdapter(List<CrossReferenceAnalyzer.XRef> r) { this.refs = r; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(12);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(28, 16, 28, 16);
            tv.setTextIsSelectable(true);
            return new VH(tv);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            Context_color c = new Context_color(h.tv.getContext());
            CrossReferenceAnalyzer.XRef r = refs.get(i);
            String mn = r.mnemonic != null ? r.mnemonic : "?";
            String name = r.targetName;
            String demangled = null;
            if (name != null && name.startsWith("_Z")) {
                Demangler.Result dm = Demangler.demangle(name);
                if (dm.supported) demangled = dm.demangled;
            }

            String kindTag;
            switch (r.kind) {
                case CALL: kindTag = "CALL"; break;
                case JUMP: kindTag = "JUMP"; break;
                case BRANCH_COND: kindTag = "BR.C"; break;
                case LOAD_ADDR: kindTag = "ADR"; break;
                case DATA_LOAD: kindTag = "LDR"; break;
                default: kindTag = "???"; break;
            }

            String text = String.format(Locale.US, "%s  0x%x:  %-4s  →  0x%x  %s%s",
                    kindTag,
                    r.fromAddr, mn, r.toAddr,
                    name != null ? name : "(unknown)",
                    demangled != null ? ("\n  ↳ " + demangled) : "");
            SpannableString sp = new SpannableString(text);
            // 类型 tag 着色
            int kindColor = c.kindColor(r.kind);
            sp.setSpan(new ForegroundColorSpan(kindColor),
                    0, kindTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    0, kindTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // 高亮名字
            if (name != null) {
                int nameIdx = text.indexOf(name, text.indexOf("→"));
                if (nameIdx >= 0) {
                    sp.setSpan(new ForegroundColorSpan(c.primary),
                            nameIdx, nameIdx + name.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            // 背景：交替行
            h.tv.setBackgroundColor(i % 2 == 0 ? c.surface : c.surfaceVariant);
            h.tv.setTextColor(c.onSurface);
            h.tv.setText(sp);
        }

        @Override public int getItemCount() { return refs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }

    /** 集中色板，跟着 ThemeUtils */
    private static class Context_color {
        final int primary, onSurface, surface, surfaceVariant;
        Context_color(android.content.Context ctx) {
            primary = ThemeUtils.colorPrimary(ctx);
            onSurface = ThemeUtils.colorOnSurface(ctx);
            surface = ThemeUtils.colorSurface(ctx);
            surfaceVariant = ThemeUtils.colorSurfaceVariant(ctx);
        }
        int kindColor(CrossReferenceAnalyzer.XRefKind k) {
            switch (k) {
                case CALL: return primary;
                case JUMP: return 0xFF1A6EF0;
                case BRANCH_COND: return 0xFF2E7D32;
                case LOAD_ADDR: return 0xFF6B5778;
                case DATA_LOAD: return 0xFF535F70;
                default: return onSurface;
            }
        }
    }
}
