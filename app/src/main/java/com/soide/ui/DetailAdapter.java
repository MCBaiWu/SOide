package com.soide.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;

import java.util.List;

public class DetailAdapter extends RecyclerView.Adapter<DetailAdapter.VH> {

    public static class Item {
        public String type;
        public String title;
        public String subtitle;
        public String meta;
    }

    public interface OnClick {
        void onClick(Item item);
    }

    private final List<Item> items;
    private final OnClick onClick;

    public DetailAdapter(List<Item> items, OnClick onClick) {
        this.items = items;
        this.onClick = onClick;
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
        Item it = items.get(position);
        h.title.setText(it.title != null ? it.title : "");
        h.subtitle.setText(it.subtitle != null ? it.subtitle : "");
        h.meta.setText(it.meta != null ? it.meta : "");
        h.type.setText(it.type != null ? it.type : "");
        h.root.setOnClickListener(v -> onClick.onClick(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
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
