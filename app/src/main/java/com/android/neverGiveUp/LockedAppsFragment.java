package com.android.neverGiveUp;

import android.view.Gravity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.HashSet;
import java.util.Set;

public class LockedAppsFragment extends Fragment {

    private ChipGroup chipGroupBrowsers;
    private LinearLayout llSocialList;
    private EditText etBrowserPackage, etSocialPackage;
    private TextView tvBrowserCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_locked_apps, container, false);

        // Bind layouts isolated inside fragment_locked_apps layout
        chipGroupBrowsers = view.findViewById(R.id.chipGroupBrowsers);
        llSocialList = view.findViewById(R.id.llSocialList);
        etBrowserPackage = view.findViewById(R.id.etBrowserPackage);
        etSocialPackage = view.findViewById(R.id.etSocialPackage);
        tvBrowserCount = view.findViewById(R.id.tvBrowserCount);

        // Process additions to custom browser packages
        Button btnAddBrowser = view.findViewById(R.id.btnAddBrowser);
        btnAddBrowser.setOnClickListener(v -> {
            String pkg = etBrowserPackage.getText().toString().trim();
            if (!pkg.isEmpty()) {
                addToSet("extra_browsers", pkg);
                etBrowserPackage.setText("");
                refreshBrowserChips();
                Toast.makeText(getActivity(), "Added to hard block.", Toast.LENGTH_SHORT).show();
            }
        });

        // Process additions to credit-gated social packages
        Button btnAddSocial = view.findViewById(R.id.btnAddSocial);
        btnAddSocial.setOnClickListener(v -> {
            String pkg = etSocialPackage.getText().toString().trim();
            if (!pkg.isEmpty()) {
                addToSet("extra_social", pkg);
                etSocialPackage.setText("");
                refreshSocialList();
                Toast.makeText(getActivity(), "Added to credit gate.", Toast.LENGTH_SHORT).show();
            }
        });

        // Dynamic inflation calls
        refreshBrowserChips();
        refreshSocialList();

        return view;
    }

    private void refreshBrowserChips() {
        if (chipGroupBrowsers == null) return;
        chipGroupBrowsers.removeAllViews();

        String[] defaults = {"Chrome", "Firefox", "Brave", "Edge", "Tor", "Opera", "Vivaldi", "DuckDuckGo", "Samsung", "UC", "Via", "Yandex"};
        for (String name : defaults) {
            chipGroupBrowsers.addView(makeChip(name, true));
        }

        Set<String> extras = getPrefs().getStringSet("extra_browsers", new HashSet<>());
        for (String pkg : extras) {
            Chip chip = makeChip(pkg, false);
            chip.setOnCloseIconClickListener(v -> {
                removeFromSet("extra_browsers", pkg);
                refreshBrowserChips();
            });
            chipGroupBrowsers.addView(chip);
        }
        if (tvBrowserCount != null) {
            tvBrowserCount.setText((54 + extras.size()) + " browsers");
        }
    }

    private void refreshSocialList() {
        if (llSocialList == null) return;
        llSocialList.removeAllViews();

        String[] defaults = {"Instagram", "YouTube", "TikTok", "Facebook", "Snapchat", "Twitter", "Discord", "Netflix", "Spotify", "Reddit", "WhatsApp", "Telegram"};
        for (String name : defaults) {
            llSocialList.addView(makeListItem(name, null));
        }

        Set<String> extras = getPrefs().getStringSet("extra_social", new HashSet<>());
        for (String pkg : extras) {
            llSocialList.addView(makeListItem(pkg, () -> {
                removeFromSet("extra_social", pkg);
                refreshSocialList();
            }));
        }
    }

    private Chip makeChip(String label, boolean isDefault) {
        Chip chip = new Chip(getActivity());
        chip.setText(label);
        chip.setTextSize(11);
        chip.setChipBackgroundColorResource(android.R.color.transparent);
        chip.setTextColor(Color.parseColor("#E57373"));
        chip.setChipStrokeWidth(1f);
        chip.setChipStrokeColorResource(android.R.color.transparent);
        if (!isDefault) chip.setCloseIconVisible(true);
        return chip;
    }

    private View makeListItem(String label, Runnable onDelete) {
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.item_bg);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        row.setPadding(pad + 4, pad, pad, pad);

        TextView tv = new TextView(getActivity());
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        row.addView(tv);

        if (onDelete != null) {
            Button del = new Button(getActivity());
            del.setText("✕");
            del.setTextSize(11);
            del.setTextColor(Color.parseColor("#E57373"));
            del.setBackgroundColor(Color.TRANSPARENT);
            del.setOnClickListener(v -> onDelete.run());
            row.addView(del);
        }

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (6 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowLp);
        return row;
    }

    private android.content.SharedPreferences getPrefs() {
        return getActivity().getSharedPreferences("relentless", android.content.Context.MODE_PRIVATE);
    }

    private void addToSet(String key, String value) {
        Set<String> set = new HashSet<>(getPrefs().getStringSet(key, new HashSet<>()));
        set.add(value);
        getPrefs().edit().putStringSet(key, set).apply();
    }

    private void removeFromSet(String key, String value) {
        Set<String> set = new HashSet<>(getPrefs().getStringSet(key, new HashSet<>()));
        set.remove(value);
        getPrefs().edit().putStringSet(key, set).apply();
    }
}