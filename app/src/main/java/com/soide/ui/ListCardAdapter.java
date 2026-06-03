package com.soide.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;

import java.util.List;

/**
 * 通用列表适配器，渲染标题/副标题/元数据三行 + 图标。
 */
public class ListCardAdapter extends RecyclerView.Adapter<ListCardAdapter.VH> {

    public static class Item {
        public int iconRes;
        public String title;
        public String subtitle;
        public String meta;
        public String tag;

        public Item(int iconRes, String title, String subtitle, String meta) {
            this(iconRes, title, subtitle, meta, null);
        }

        public Item(int iconRes, String title, String subtitle, String meta, String tag) {
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
            this.meta = meta;
            this.tag = tag;
        }
    }

    public interface OnClick {
        void onClick(Item item);
    }

    private final List<Item> items;
    private final OnClick onClick;

    public ListCardAdapter(List<Item> items, OnClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_list_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);
        h.icon.setImageResource(it.iconRes);
        h.title.setText(it.title);
        h.subtitle.setText(it.subtitle);
        h.meta.setText(it.meta);
        h.root.setOnClickListener(v -> onClick.onClick(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final CardView root;
        final ImageView icon;
        final TextView title, subtitle, meta;

        VH(@NonNull View v) {
            super(v);
            root = (CardView) v;
            icon = v.findViewById(R.id.iv_icon);
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
            meta = v.findViewById(R.id.tv_meta);
        }
    }
}
