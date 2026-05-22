package com.Rixnar.NeverGiveUp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class LockedAppsFragment extends Fragment {

    private TextView tvHardCount;
    private TextView tvSocialCount;
    private RecyclerView rvHardPreview;
    private RecyclerView rvSocialPreview;

    private AppListAdapter hardAdapter;
    private AppListAdapter socialAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_locked_apps, container, false);

        tvHardCount = view.findViewById(R.id.tvHardCount);
        tvSocialCount = view.findViewById(R.id.tvSocialCount);
        rvHardPreview = view.findViewById(R.id.rvHardPreview);
        rvSocialPreview = view.findViewById(R.id.rvSocialPreview);

        rvHardPreview.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSocialPreview.setLayoutManager(new LinearLayoutManager(getActivity()));

        hardAdapter = new AppListAdapter(id -> {
            // Deletions for preview list are not supported (all edits happen inside the bottom sheet),
            // but keep this safe no-op.
        });
        socialAdapter = new AppListAdapter(id -> {});

        rvHardPreview.setAdapter(hardAdapter);
        rvSocialPreview.setAdapter(socialAdapter);

        Button btnViewAllHard = view.findViewById(R.id.btnViewAllHard);
        btnViewAllHard.setOnClickListener(v -> openSheet("Hard locked apps", "extra_browsers", "browser"));

        Button btnViewAllSocial = view.findViewById(R.id.btnViewAllSocial);
        btnViewAllSocial.setOnClickListener(v -> openSheet("Credit gated apps", "extra_social", "social"));

        refreshPreviews();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPreviews();
    }

    private void openSheet(String title, String prefKey, String builtinType) {
        AppListBottomSheetDialog dialog = AppListBottomSheetDialog.newInstance(title, prefKey, builtinType);
        dialog.setOnListChangedListener(this::refreshPreviews);
        dialog.show(getParentFragmentManager(), "app_list_sheet_" + prefKey);
    }

    private void refreshPreviews() {
        if (getActivity() == null) return;

        List<AppListAdapter.Row> hardPreview = buildPreviewRows("extra_browsers", "browser", 4);
        List<AppListAdapter.Row> socialPreview = buildPreviewRows("extra_social", "social", 4);

        hardAdapter.submitList(hardPreview);
        socialAdapter.submitList(socialPreview);

        if (tvHardCount != null) tvHardCount.setText(String.valueOf(countAll("extra_browsers", "browser")));
        if (tvSocialCount != null) tvSocialCount.setText(String.valueOf(countAll("extra_social", "social")));
    }

    private int countAll(String prefKey, String builtinType) {
        // Count unique display names to avoid "5 Chromes" in the UX.
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        String[] pkgs = "browser".equals(builtinType) ? AppCatalog.BROWSER_PACKAGES : AppCatalog.CREDIT_GATED_PACKAGES;
        for (String pkg : pkgs) {
            String name = AppLabelUtil.toDisplayNameOrFallback(getActivity(), pkg);
            unique.put(normalizeKey(name), true);
        }
        Set<String> extras = getPrefs().getStringSet(prefKey, new HashSet<>());
        for (String s : extras) {
            String name = AppLabelUtil.toDisplayNameOrFallback(getActivity(), s);
            unique.put(normalizeKey(name), true);
        }
        return unique.size();
    }

    private List<AppListAdapter.Row> buildPreviewRows(String prefKey, String builtinType, int limit) {
        LinkedHashMap<String, AppListAdapter.Row> byName = new LinkedHashMap<>();

        // Built-in INSTALLED apps first (best UX).
        String[] pkgs = "browser".equals(builtinType) ? AppCatalog.BROWSER_PACKAGES : AppCatalog.CREDIT_GATED_PACKAGES;
        for (String pkg : pkgs) {
            String installedLabel = AppLabelUtil.getInstalledLabelOrNull(getActivity(), pkg);
            if (installedLabel != null && !installedLabel.equals(pkg)) {
                String key = normalizeKey(installedLabel);
                if (!byName.containsKey(key)) {
                    byName.put(key, new AppListAdapter.Row(pkg, installedLabel, false));
                    if (byName.size() >= limit) return new ArrayList<>(byName.values());
                }
            }
        }

        // If none installed (or still not enough), show top built-in suggestions anyway.
        for (String pkg : pkgs) {
            if (byName.size() >= limit) return new ArrayList<>(byName.values());
            String name = AppLabelUtil.toDisplayNameOrFallback(getActivity(), pkg);
            String key = normalizeKey(name);
            if (!byName.containsKey(key)) {
                byName.put(key, new AppListAdapter.Row(pkg, name, false));
            }
        }

        // Then custom extras.
        Set<String> extras = getPrefs().getStringSet(prefKey, new HashSet<>());
        for (String s : extras) {
            if (byName.size() >= limit) return new ArrayList<>(byName.values());
            String name = AppLabelUtil.toDisplayNameOrFallback(getActivity(), s);
            String key = normalizeKey(name);
            if (!byName.containsKey(key)) {
                byName.put(key, new AppListAdapter.Row(s, name, false));
            }
        }

        return new ArrayList<>(byName.values());
    }

    private android.content.SharedPreferences getPrefs() {
        return getActivity().getSharedPreferences("relentless", android.content.Context.MODE_PRIVATE);
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }
}
