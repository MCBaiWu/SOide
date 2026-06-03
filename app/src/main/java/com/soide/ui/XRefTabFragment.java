package com.soide.ui;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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

import java.util.List;

/**
 * 函数详情 - 交叉引用 tab
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

        // 隐藏搜索栏
        View til = view.findViewById(R.id.til_search);
        if (til != null) til.setVisibility(View.GONE);

        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_KEY) : null);
        if (d == null || d.function == null) {
            tvCount.setText("交叉引用");
            return;
        }
        List<CrossReferenceAnalyzer.XRef> refs = CrossReferenceAnalyzer.find(
                d.function.instructions, d.symbols, d.imports);
        tvCount.setText("交叉引用  ·  " + refs.size() + " 处");
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
            CrossReferenceAnalyzer.XRef r = refs.get(i);
            String mn = r.mnemonic != null ? r.mnemonic : "?";
            String name = r.targetName;
            String demangled = null;
            if (name != null && name.startsWith("_Z")) {
                Demangler.Result dm = Demangler.demangle(name);
                if (dm.supported) demangled = dm.demangled;
            }
            String text = String.format("0x%x:  %-4s  →  0x%x  %s%s",
                    r.fromAddr, mn, r.toAddr,
                    name != null ? name : "(unknown)",
                    demangled != null ? ("\n  ↳ " + demangled) : "");
            SpannableString sp = new SpannableString(text);
            // 高亮名字
            if (name != null) {
                int nameIdx = text.indexOf(name, text.indexOf("→"));
                if (nameIdx >= 0) {
                    sp.setSpan(new ForegroundColorSpan(0xFF1A6EF0),
                            nameIdx, nameIdx + name.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            h.tv.setText(sp);
        }

        @Override public int getItemCount() { return refs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }
}
