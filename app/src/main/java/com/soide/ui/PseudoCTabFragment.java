package com.soide.ui;

import android.os.Bundle;
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
import com.soide.elf.pseudoc.PseudoCContext;
import com.soide.elf.pseudoc.PseudoCConverter;
import com.soide.elf.pseudoc.PseudoCRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数详情 - 伪 C tab
 */
public class PseudoCTabFragment extends Fragment {

    private static final String ARG_KEY = "key";

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

        // 隐藏搜索栏
        View til = view.findViewById(R.id.til_search);
        if (til != null) til.setVisibility(View.GONE);

        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_KEY) : null);
        if (d == null || d.function == null) {
            tvCount.setText("伪 C  •  不可用");
            return;
        }

        // 构造 context
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
            tv.setPadding(24, 8, 24, 8);
            tv.setTextIsSelectable(true);
            return new VH(tv);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            h.tv.setText(lines.get(i));
        }

        @Override public int getItemCount() { return lines.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) { super(v); this.tv = (TextView) v; }
        }
    }
}
