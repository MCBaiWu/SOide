package com.soide.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.FunctionInfo;

import java.util.List;

public class FuncListAdapter extends RecyclerView.Adapter<FuncListAdapter.VH> {

    public interface OnClick {
        void onClick(FunctionInfo f);
    }

    private List<FunctionInfo> data;
    private final OnClick onClick;

    public FuncListAdapter(List<FunctionInfo> data, OnClick onClick) {
        this.data = data;
        this.onClick = onClick;
    }

    public void setData(List<FunctionInfo> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FunctionInfo f = data.get(position);
        h.title.setText(f.name != null ? f.name : "(unnamed)");

        StringBuilder sub = new StringBuilder();
        sub.append("size=").append(f.size).append("  sec=")
                .append(f.sectionName != null ? f.sectionName : "?");
        if (f.isThumb) sub.append("  [Thumb]");
        h.subtitle.setText(sub.toString());

        h.meta.setText(String.format("addr=0x%x  insns=%d", f.address,
                f.instructions != null ? f.instructions.size() : 0));
        String tag = f.source == 1 ? "LinearSweep" : "Symbol";
        h.type.setText(tag);
        h.root.setOnClickListener(v -> onClick.onClick(f));
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final CardView root;
        final TextView title, subtitle, meta, type;

        VH(@NonNull View v) {
            super(v);
            root = (CardView) v;
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
            meta = v.findViewById(R.id.tv_meta);
            type = v.findViewById(R.id.tv_type);
        }
    }
}
