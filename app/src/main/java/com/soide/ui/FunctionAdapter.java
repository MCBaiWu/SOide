package com.soide.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.elf.FunctionInfo;

import java.util.List;

/**
 * 函数列表 Adapter
 */
public class FunctionAdapter extends RecyclerView.Adapter<FunctionAdapter.VH> {

    public interface OnFunctionClickListener {
        void onClick(FunctionInfo fi);
    }

    private final List<FunctionInfo> items;
    private final OnFunctionClickListener listener;

    public FunctionAdapter(List<FunctionInfo> items, OnFunctionClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_list, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FunctionInfo fi = items.get(position);
        holder.tvName.setText(fi.name);
        holder.tvSubtitle.setText(String.format("0x%x  ·  %d bytes  ·  %s",
                fi.address, fi.size, fi.sectionName));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(fi);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialTextView tvName, tvSubtitle;
        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_name);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
        }
    }
}