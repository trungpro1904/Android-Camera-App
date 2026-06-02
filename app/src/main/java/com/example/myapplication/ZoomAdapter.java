package com.example.myapplication;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class ZoomAdapter extends RecyclerView.Adapter<ZoomAdapter.ZoomViewHolder> {

    private final List<Float> zoomRatios;
    private final OnZoomClickListener listener;
    private int selectedPosition = -1;

    public interface OnZoomClickListener {
        void onZoomClick(float ratio, int position);
    }

    public ZoomAdapter(List<Float> ratios, OnZoomClickListener listener) {
        this.zoomRatios = ratios;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ZoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zoom, parent, false);
        return new ZoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ZoomViewHolder holder, int position) {
        float ratio = zoomRatios.get(position);
        holder.bind(ratio, position == selectedPosition);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onZoomClick(ratio, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return zoomRatios.size();
    }

    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    static class ZoomViewHolder extends RecyclerView.ViewHolder {
        TextView tvValue;

        public ZoomViewHolder(@NonNull View itemView) {
            super(itemView);
            tvValue = itemView.findViewById(R.id.tvZoomValue);
        }

        void bind(float ratio, boolean isSelected) {
            tvValue.setText(String.format(Locale.US, "%.1fx", ratio));
            tvValue.setBackgroundResource(R.drawable.bg_circle_border);
            tvValue.setTextColor(Color.WHITE);
            
            float scale = isSelected ? 1.25f : 1.0f;
            tvValue.animate().scaleX(scale).scaleY(scale).setDuration(200).start();
        }
    }
}
