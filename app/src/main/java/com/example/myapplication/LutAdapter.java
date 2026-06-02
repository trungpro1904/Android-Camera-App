package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LutAdapter extends RecyclerView.Adapter<LutAdapter.ViewHolder> {

    private final List<LutItem> items;
    private int selectedPosition = 0;
    private OnLutSelectedListener listener;

    public interface OnLutSelectedListener {
        void onLutSelected(LutItem item, int position);
    }

    public LutAdapter(List<LutItem> items, OnLutSelectedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lut, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LutItem item = items.get(position);
        holder.tvLutName.setText(item.getName());

        if (position == selectedPosition) {
            holder.container.setAlpha(1.0f);
            holder.container.setScaleX(1.1f);
            holder.container.setScaleY(1.1f);
        } else {
            holder.container.setAlpha(0.6f);
            holder.container.setScaleX(1.0f);
            holder.container.setScaleY(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onLutSelected(item, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setSelectedPosition(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLutName;
        View container;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLutName = itemView.findViewById(R.id.tvLutName);
            container = itemView.findViewById(R.id.lutContainer);
        }
    }
}

