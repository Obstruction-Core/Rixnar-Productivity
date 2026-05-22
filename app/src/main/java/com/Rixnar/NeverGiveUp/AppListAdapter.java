package com.Rixnar.NeverGiveUp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.VH> {

    public interface Listener {
        void onDeleteClicked(String id);
    }

    public static class Row {
        public final String id;           // package name OR label
        public final String displayName;  // user-facing label
        public final boolean deletable;

        public Row(String id, String displayName, boolean deletable) {
            this.id = id;
            this.displayName = displayName;
            this.deletable = deletable;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public AppListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Row> newRows) {
        rows.clear();
        if (newRows != null) rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Row row = rows.get(position);
        holder.tvAppName.setText(row.displayName);
        holder.btnDelete.setVisibility(row.deletable ? View.VISIBLE : View.GONE);
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClicked(row.id);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAppName;
        TextView btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}

