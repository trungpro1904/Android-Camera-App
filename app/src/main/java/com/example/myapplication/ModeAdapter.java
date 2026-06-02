package com.example.myapplication;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ModeAdapter extends RecyclerView.Adapter<ModeAdapter.ModeViewHolder> {

    private final List<String> modes;
    private final OnModeClickListener listener;
    private int selectedPosition = 0; // Default to first item

    public interface OnModeClickListener {
        void onModeClick(String mode, int position);
    }

    public ModeAdapter(List<String> modes, OnModeClickListener listener) {
        this.modes = modes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mode, parent, false);
        return new ModeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModeViewHolder holder, int position) {
        String mode = modes.get(position);
        holder.bind(mode, position == selectedPosition);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onModeClick(mode, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return modes.size();
    }

    public void setSelectedPosition(int position) {
        if (position == selectedPosition) return;
        int oldPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    static class ModeViewHolder extends RecyclerView.ViewHolder {
        TextView modeName;

        public ModeViewHolder(@NonNull View itemView) {
            super(itemView);
            modeName = (TextView) itemView;
        }

        void bind(String mode, boolean isSelected) {
            modeName.setText(mode);
            if (isSelected) {
                modeName.setTextColor(Color.WHITE);
                modeName.setTypeface(null, Typeface.BOLD);
            } else {
                modeName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.camera_accent_orange));
                modeName.setTypeface(null, Typeface.NORMAL);
            }
        }
    }
}

