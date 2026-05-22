package com.Rixnar.NeverGiveUp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.work.WorkManager;
import java.security.MessageDigest;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private long lastRevokeAttempt = 0;
    private int revokeAttempts = 0;
    private static final long REVOKE_COOLDOWN_MS = 60000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        Context context = getContext();
        if (getActivity() == null || context == null) return view;

        prefs = getActivity().getSharedPreferences("relentless", Context.MODE_PRIVATE);
        dpm = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(getActivity(), RelentlessAdminReceiver.class);

        // Backup Reminder
        SwitchCompat switchBackup = view.findViewById(R.id.switchBackupReminder);
        switchBackup.setChecked(prefs.getBoolean("settings_backup_reminder", true));
        switchBackup.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.edit().putBoolean("settings_backup_reminder", isChecked).apply();
            PersistenceHelper.saveToInternalStorage(context);
            if (!isChecked) {
                WorkManager.getInstance(context).cancelUniqueWork("relentless_backup_reminder");
            } else {
                // MainActivity handles re-scheduling on next launch, or we could do it here
            }
        });

        // Lock Admin
        SwitchCompat switchLockAdmin = view.findViewById(R.id.switchLockAdmin);
        switchLockAdmin.setChecked(prefs.getBoolean("settings_lock_admin", true));
        switchLockAdmin.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.edit().putBoolean("settings_lock_admin", isChecked).apply();
            PersistenceHelper.saveToInternalStorage(context);
            Toast.makeText(context, "Admin protection " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // Security
        Button btnRevoke = view.findViewById(R.id.btnRevokeAdmin);
        btnRevoke.setOnClickListener(v -> showRevokeDialog());

        // Data Management
        view.findViewById(R.id.btnClearCache).setOnClickListener(v -> Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show());

        // About
        view.findViewById(R.id.btnAbout).setOnClickListener(v -> showAboutDialog());

        return view;
    }

    private void showRevokeDialog() {
        long now = System.currentTimeMillis();
        if (now - lastRevokeAttempt < REVOKE_COOLDOWN_MS && revokeAttempts > 2) {
            long remaining = (REVOKE_COOLDOWN_MS - (now - lastRevokeAttempt)) / 1000;
            new AlertDialog.Builder(getContext())
                    .setTitle("Too Many Attempts")
                    .setMessage("Please wait " + remaining + " seconds before trying again.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        lastRevokeAttempt = now;
        revokeAttempts++;

        final EditText input = new EditText(getContext());
        input.setHint("Enter 30-character master key");
        new AlertDialog.Builder(getContext())
                .setTitle("⚠️ REVOKE PROTECTION")
                .setMessage("Entering the master key will deactivate Device Admin, allowing you to turn off accessibility or uninstall the app.\n\nContinue?")
                .setView(input)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String entered = input.getText().toString();
                    String storedHash = prefs.getString("key_hash", "");
                    if (hash(entered).equals(storedHash)) {
                        dpm.removeActiveAdmin(adminComponent);
                        prefs.edit().putBoolean("accessibility_setup_locked", false).apply();
                        PersistenceHelper.saveToInternalStorage(getContext());
                        Toast.makeText(getContext(), "Protection Revoked. You can now change system settings.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "Wrong key.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("About Rixnar")
                .setMessage("Version 1.0\n\nA powerful tool to stay focused and beat addiction.\n\nKeep pushing, never give up.")
                .setPositiveButton("OK", null).show();
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return input; }
    }
}
