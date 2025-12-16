package com.example.imageblog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.VH> {
    private List<StatsItem> items;

    public StatsAdapter(List<StatsItem> items) {
        this.items = items;
    }

    public void update(List<StatsItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stats, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        StatsItem it = items.get(position);
        holder.name.setText(it.getName());
        holder.count.setText(String.valueOf(it.getCount()));
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView count;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.item_name);
            count = itemView.findViewById(R.id.item_count);
        }
    }
}

