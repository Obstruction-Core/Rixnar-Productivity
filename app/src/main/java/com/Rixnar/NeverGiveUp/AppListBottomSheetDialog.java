package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListBottomSheetDialog extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_PREF_KEY = "pref_key";
    private static final String ARG_BUILTIN = "builtin"; // "browser" | "social"

    public interface OnListChangedListener {
        void onListChanged();
    }

    private OnListChangedListener listener;

    public static AppListBottomSheetDialog newInstance(String title, String prefKey, String builtinType) {
        AppListBottomSheetDialog d = new AppListBottomSheetDialog();
        Bundle b = new Bundle();
        b.putString(ARG_TITLE, title);
        b.putString(ARG_PREF_KEY, prefKey);
        b.putString(ARG_BUILTIN, builtinType);
        d.setArguments(b);
        return d;
    }

    public void setOnListChangedListener(OnListChangedListener listener) {
        this.listener = listener;
    }

    private SharedPreferences prefs() {
        return requireActivity().getSharedPreferences("relentless", Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottom_sheet_app_list, container, false);

        String title = getArguments() != null ? getArguments().getString(ARG_TITLE, "Apps") : "Apps";
        String prefKey = getArguments() != null ? getArguments().getString(ARG_PREF_KEY, "") : "";
        String builtin = getArguments() != null ? getArguments().getString(ARG_BUILTIN, "") : "";

        TextView tvTitle = v.findViewById(R.id.tvSheetTitle);
        EditText etPackage = v.findViewById(R.id.etPackage);
        Button btnAdd = v.findViewById(R.id.btnAdd);
        Button btnSelectApp = v.findViewById(R.id.btnSelectApp);
        RecyclerView rv = v.findViewById(R.id.rvApps);

        tvTitle.setText(title);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        final AppListAdapter[] adapterHolder = new AppListAdapter[1];
        adapterHolder[0] = new AppListAdapter(id -> {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) requireActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName adminComponent = new android.content.ComponentName(requireActivity(), RelentlessAdminReceiver.class);
            
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                Toast.makeText(getContext(), "Cannot remove apps while protection is active.", Toast.LENGTH_LONG).show();
                return;
            }

            removeFromSet(prefKey, id);
            refreshList(adapterHolder[0], builtin, prefKey);
            if (listener != null) listener.onListChanged();
            PersistenceHelper.saveToInternalStorage(getContext());
        });
        rv.setAdapter(adapterHolder[0]);

        btnSelectApp.setOnClickListener(x -> showInstalledAppsDialog(prefKey, builtin, adapterHolder[0]));

        btnAdd.setOnClickListener(x -> {
            String pkg = etPackage.getText().toString().trim();
            if (pkg.isEmpty()) return;
            processAppAddition(pkg, prefKey, builtin, adapterHolder[0]);
            etPackage.setText("");
        });

        refreshList(adapterHolder[0], builtin, prefKey);
        return v;
    }

    private void showInstalledAppsDialog(String currentPrefKey, String builtin, AppListAdapter adapter) {
        Context context = getContext();
        if (context == null) return;
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        // Sort apps by name
        packages.sort((a, b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        List<String> appNames = new ArrayList<>();
        List<String> pkgNames = new ArrayList<>();

        for (ApplicationInfo ai : packages) {
            // Filter out system apps that might be dangerous to block or unnecessary?
            // For now, show all except our own app.
            if (ai.packageName.equals(context.getPackageName())) continue;
            
            // Only show apps with a launcher intent (real apps)
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;

            // Exclude apps already in the CURRENT list
            if (isAlreadyRepresented(builtin, currentPrefKey, ai.packageName)) continue;

            String label = pm.getApplicationLabel(ai).toString();
            appNames.add(label);
            pkgNames.add(ai.packageName);
        }

        new AlertDialog.Builder(context)
                .setTitle("Select App to Block")
                .setItems(appNames.toArray(new String[0]), (dialog, which) -> {
                    String selectedPkg = pkgNames.get(which);
                    processAppAddition(selectedPkg, currentPrefKey, builtin, adapter);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processAppAddition(String pkg, String currentPrefKey, String builtin, AppListAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        if (isAlreadyRepresented(builtin, currentPrefKey, pkg)) {
            Toast.makeText(getActivity(), "Already in this list.", Toast.LENGTH_SHORT).show();
            return;
        }

        String otherPrefKey = currentPrefKey.equals("extra_browsers") ? "extra_social" : "extra_browsers";
        boolean inOtherList = prefs().getStringSet(otherPrefKey, new HashSet<>()).contains(pkg);

        if (currentPrefKey.equals("extra_browsers")) {
            // Adding to HARD BLOCK
            if (inOtherList) {
                // Scenario 1: in credit-gated, moving to hard-blocked
                new AlertDialog.Builder(context)
                        .setTitle("⚠️ Move to Hard Block")
                        .setMessage("This app is currently in Credit Gated apps. Do you want to move it to Hard Blocked?\n\nThis action cannot be undone and will lock the app completely.")
                        .setPositiveButton("Move to Hard Block", (d, w) -> {
                            removeFromSet(otherPrefKey, pkg);
                            addToSet(currentPrefKey, pkg);
                            completeAddition(adapter, builtin, currentPrefKey);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                new AlertDialog.Builder(context)
                        .setTitle("⚠️ Confirm Hard Block")
                        .setMessage("Are you sure you want to add this app to Hard Blocked?\n\nThis action cannot be undone.")
                        .setPositiveButton("Hard Block", (d, w) -> {
                            addToSet(currentPrefKey, pkg);
                            completeAddition(adapter, builtin, currentPrefKey);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        } else {
            // Adding to CREDIT GATED
            if (inOtherList) {
                // Scenario 2: in hard-block, trying to add to credit-gated
                new AlertDialog.Builder(context)
                        .setTitle("Action Not Possible")
                        .setMessage("This app is already Hard Blocked. You cannot add it to Credit Gated apps.")
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                new AlertDialog.Builder(context)
                        .setTitle("Confirm Credit Gated")
                        .setMessage("Add this app to Credit Gated apps?\n\nThis action cannot be undone.")
                        .setPositiveButton("Add", (d, w) -> {
                            addToSet(currentPrefKey, pkg);
                            completeAddition(adapter, builtin, currentPrefKey);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    private void completeAddition(AppListAdapter adapter, String builtin, String prefKey) {
        Toast.makeText(getActivity(), "Added.", Toast.LENGTH_SHORT).show();
        refreshList(adapter, builtin, prefKey);
        if (listener != null) listener.onListChanged();
        PersistenceHelper.saveToInternalStorage(getContext());
    }

    private void refreshList(AppListAdapter adapter, String builtin, String prefKey) {
        // De-dupe by display name (so variants like Chrome Beta/Dev don't spam the list).
        LinkedHashMap<String, AppListAdapter.Row> byName = new LinkedHashMap<>();

        // Built-in catalog (non-deletable). Show even if not installed, using friendly fallback names.
        String[] builtinPkgs = "browser".equals(builtin) ? AppCatalog.BROWSER_PACKAGES : AppCatalog.CREDIT_GATED_PACKAGES;
        if (getContext() != null) {
            for (String pkg : builtinPkgs) {
                String name = AppLabelUtil.toDisplayNameOrFallback(getContext(), pkg);
                String key = normalizeKey(name);
                if (!byName.containsKey(key)) {
                    byName.put(key, new AppListAdapter.Row(pkg, name, false));
                }
            }
        }

        // Custom extras (deletable)
        Set<String> extras = prefs().getStringSet(prefKey, new HashSet<>());
        for (String s : extras) {
            String name = AppLabelUtil.toDisplayNameOrFallback(getContext(), s);
            String key = normalizeKey(name);
            if (!byName.containsKey(key)) {
                byName.put(key, new AppListAdapter.Row(s, name, true));
            }
        }

        adapter.submitList(new ArrayList<>(byName.values()));
    }

    private boolean isAlreadyRepresented(String builtin, String prefKey, String pkgOrLabel) {
        if (pkgOrLabel == null) return false;
        String name = AppLabelUtil.toDisplayNameOrFallback(getContext(), pkgOrLabel);
        String key = normalizeKey(name);

        String[] builtinPkgs = "browser".equals(builtin) ? AppCatalog.BROWSER_PACKAGES : AppCatalog.CREDIT_GATED_PACKAGES;
        for (String p : builtinPkgs) {
            if (pkgOrLabel.equals(p)) return true;
            String existingName = AppLabelUtil.toDisplayNameOrFallback(getContext(), p);
            if (normalizeKey(existingName).equals(key)) return true;
        }

        Set<String> extras = prefs().getStringSet(prefKey, new HashSet<>());
        for (String e : extras) {
            if (pkgOrLabel.equals(e)) return true;
            String existingName = AppLabelUtil.toDisplayNameOrFallback(getContext(), e);
            if (normalizeKey(existingName).equals(key)) return true;
        }

        return false;
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    private void addToSet(String key, String value) {
        Set<String> set = new HashSet<>(prefs().getStringSet(key, new HashSet<>()));
        set.add(value);
        prefs().edit().putStringSet(key, set).commit();
    }

    private void removeFromSet(String key, String value) {
        Set<String> set = new HashSet<>(prefs().getStringSet(key, new HashSet<>()));
        set.remove(value);
        prefs().edit().putStringSet(key, set).commit();
    }
}
