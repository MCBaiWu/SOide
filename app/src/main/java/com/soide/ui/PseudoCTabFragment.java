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
import com.soide.elf.pseudoc.PseudoCConverter;
import com.soide.elf.pseudoc.PseudoCConverter.PseudoCContext;
import com.soide.elf.pseudoc.PseudoCRegistry;
import com.soide.util.ThemeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 函数详情 - 伪 C tab
 * v1.4.3 改进：颜色用 ThemeUtils 跟随 day/night；标签/注释/调用分类着色。
 */
public class PseudoCTabFragment extends Fragment {

    private static final String ARG_KEY = "key";
    private static final Pattern LABEL_RE = Pattern.compile("^L_[0-9a-fA-F]+:\\s*$");
    private static final Pattern CALL_RE = Pattern.compile("\\b(call|.*)\\(\\)");

    public static PseudoCTabFragment newInstance(FuncDetailData d) {
        PseudoCTabFragment f = new PseudoCTabFragment();
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
            tvCount.setText("伪 C  •  不可用");
            return;
        }

        Map<Long, String> labels = new HashMap<>();
        Map<Long, String> imports = new HashMap<>();
        if (d.symbols != null) {
            for (var s : d.symbols) {
                if (s.stValue != 0 && s.name != null) labels.put(s.stValue, s.name);
            }
        }
        if (d.imports != null) {
            for (var imp : d.imports) {
                if (imp.pltAddress != 0 && imp.name != null) imports.put(imp.pltAddress, imp.name);
            }
        }

        PseudoCContext ctx = new PseudoCContext(
                d.function.name, d.function.address, d.function.size,
                d.machine, d.function.isThumb, d.function.instructions);
        ctx.labels = labels;
        ctx.imports = imports;

        PseudoCConverter conv = PseudoCRegistry.create(null);
        List<String> lines = conv.convert(ctx);

        tvCount.setText("伪 C  •  " + conv.name() + "  •  " + lines.size() + " 行");
        rv.setAdapter(new PseudoCAdapter(lines));
    }

    static class PseudoCAdapter extends RecyclerView.Adapter<PseudoCAdapter.VH> {
        private final List<String> lines;
        PseudoCAdapter(List<String> l) { this.lines = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(11);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(28, 8, 28, 8);
            tv.setTextIsSelectable(true);
            return new VH(tv);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            String line = lines.get(i);
            int primary = ThemeUtils.colorPrimary(h.tv.getContext());
            int onSurface = ThemeUtils.colorOnSurface(h.tv.getContext());
            int onSurfaceVariant = ThemeUtils.colorOnSurfaceVariant(h.tv.getContext());
            int surface = ThemeUtils.colorSurface(h.tv.getContext());
            int surfaceVariant = ThemeUtils.colorSurfaceVariant(h.tv.getContext());

            SpannableString sp = new SpannableString(line);
            // 1) L_X: 标签蓝色加粗
            if (LABEL_RE.matcher(line).matches()) {
                sp.setSpan(new ForegroundColorSpan(primary), 0, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 2) 注释 // 或 //
            int cmtIdx = line.indexOf("//");
            if (cmtIdx >= 0) {
                sp.setSpan(new ForegroundColorSpan(onSurfaceVariant), cmtIdx, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 3) 调用 foo();
            Matcher m = CALL_RE.matcher(line);
            while (m.find()) {
                int s = m.start();
                int e = m.end();
                if (s < 0 || e <= s) continue;
                sp.setSpan(new ForegroundColorSpan(primary), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 4) void xxx
            if (line.startsWith("void ")) {
                int paren = line.indexOf('(');
                if (paren > 0) {
                    sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, paren,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            // 5) return;
            if (line.contains("return")) {
                int idx = line.indexOf("return");
                sp.setSpan(new ForegroundColorSpan(0xFFC62828), idx, idx + 6,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            h.tv.setText(sp);
            h.tv.setTextColor(onSurface);
            h.tv.setBackgroundColor(i % 2 == 0 ? surface : surfaceVariant);
        }

        @Override public int getItemCount() { return lines.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }
}
