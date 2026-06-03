package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
 * v1.4.5 改进：
 * - 颜色用 ThemeUtils 跟随 day/night；标签/注释/调用/控制流分类着色
 * - 顶部 "复制全部" 按钮：复制完整伪 C 文本
 * - 行点击复制单行
 * - 单 TextView 改为：标签/关键字/数字/字符串 分类高亮
 */
public class PseudoCTabFragment extends Fragment {

    private static final String ARG_KEY = "key";
    private static final Pattern LABEL_RE = Pattern.compile("^L_[0-9a-fA-F]+:\\s*$");
    private static final Pattern CALL_RE = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern STR_RE = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern NUM_RE = Pattern.compile("\\b0x[0-9a-fA-F]+\\b|\\b\\d+\\b");
    private static final Pattern KW_RE = Pattern.compile(
            "\\b(if|else|while|for|return|goto|switch|case|break|continue|sizeof|do|void|int|long|short|char|unsigned|signed|float|double|const|static|extern|struct|union|enum|typedef|nullptr|NULL|true|false)\\b");

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

        // tvCount 已存在，我们把它转成 "复制全部" 按钮
        tvCount.setClickable(true);
        tvCount.setFocusable(true);
        tvCount.setBackground(android.graphics.drawable.ripple.ColorDrawable());
        tvCount.setTextColor(ThemeUtils.colorPrimary(requireContext()));
        tvCount.setOnClickListener(v -> {
            // 复制全部伪 C
            if (cachedLines == null || cachedLines.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (String line : cachedLines) sb.append(line).append('\n');
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("soide-pseudoc", sb.toString()));
                Toast.makeText(requireContext(), "已复制 " + cachedLines.size() + " 行", Toast.LENGTH_SHORT).show();
            }
        });

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
        cachedLines = lines;

        tvCount.setText("伪 C  ·  " + conv.name() + "  ·  " + lines.size() + " 行 (点击复制全部)");
        rv.setAdapter(new PseudoCAdapter(lines));
    }

    private List<String> cachedLines;

    class PseudoCAdapter extends RecyclerView.Adapter<PseudoCAdapter.VH> {
        private final List<String> lines;
        PseudoCAdapter(List<String> l) { this.lines = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(dp(parent, 14), dp(parent, 6), dp(parent, 14), dp(parent, 6));
            tv.setTextIsSelectable(true);
            tv.setBackground(android.graphics.drawable.ripple.ColorDrawable());
            tv.setClickable(true);
            tv.setFocusable(true);
            return new VH(tv);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            String line = lines.get(i);
            int primary = ThemeUtils.colorPrimary(h.tv.getContext());
            int onSurface = ThemeUtils.colorOnSurface(h.tv.getContext());
            int onSurfaceVariant = ThemeUtils.colorOnSurfaceVariant(h.tv.getContext());
            int surface = ThemeUtils.colorSurface(h.tv.getContext());
            int surfaceVariant = ThemeUtils.colorSurfaceVariant(h.tv.getContext());
            int error = 0xFFC62828;
            int branch = 0xFF1565C0;
            int string = 0xFF2E7D32;

            SpannableString sp = new SpannableString(line);

            // 1) 标签 L_xxx: 蓝色加粗
            if (LABEL_RE.matcher(line).matches()) {
                sp.setSpan(new ForegroundColorSpan(primary), 0, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 2) 注释 // ...
            int cmtIdx = line.indexOf("//");
            if (cmtIdx >= 0) {
                sp.setSpan(new ForegroundColorSpan(onSurfaceVariant), cmtIdx, line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 3) 关键字
            Matcher kw = KW_RE.matcher(line);
            while (kw.find()) {
                int s = kw.start(), e = kw.end();
                sp.setSpan(new ForegroundColorSpan(branch), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 4) 字符串 "..."
            Matcher sm = STR_RE.matcher(line);
            while (sm.find()) {
                int s = sm.start(), e = sm.end();
                sp.setSpan(new ForegroundColorSpan(string), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 5) 数字 0xFF / 123
            Matcher nm = NUM_RE.matcher(line);
            while (nm.find()) {
                int s = nm.start(), e = nm.end();
                sp.setSpan(new ForegroundColorSpan(0xFFE65100), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 6) 函数调用 foo()
            Matcher m = CALL_RE.matcher(line);
            while (m.find()) {
                int s = m.start(1), e = m.end(1);
                if (s < 0 || e <= s) continue;
                // 跳过关键字
                if (isKeyword(line, s, e)) continue;
                sp.setSpan(new ForegroundColorSpan(primary), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 7) return 红色
            if (line.contains("return")) {
                int idx = line.indexOf("return");
                sp.setSpan(new ForegroundColorSpan(error), idx, idx + 6,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), idx, idx + 6,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            h.tv.setText(sp);
            h.tv.setTextColor(onSurface);
            h.tv.setBackgroundColor(i % 2 == 0 ? surface : surfaceVariant);

            h.tv.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) h.tv.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("soide", line));
                    Toast.makeText(h.tv.getContext(), "已复制", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private boolean isKeyword(String line, int s, int e) {
            String t = line.substring(s, e).toLowerCase(Locale.ROOT);
            return t.equals("if") || t.equals("while") || t.equals("for") || t.equals("switch")
                    || t.equals("sizeof") || t.equals("return") || t.equals("goto")
                    || t.equals("void") || t.equals("int") || t.equals("long") || t.equals("short")
                    || t.equals("char") || t.equals("unsigned") || t.equals("signed");
        }

        @Override public int getItemCount() { return lines.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }

    private static int dp(View parent, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                parent.getResources().getDisplayMetrics());
    }
}
