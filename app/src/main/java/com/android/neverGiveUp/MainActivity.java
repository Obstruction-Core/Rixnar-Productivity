package com.android.neverGiveUp;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import android.content.DialogInterface;
import android.view.Window;

public class MainActivity extends AppCompatActivity {

    static { System.loadLibrary("relentless"); }
    public native int nativeGetCredits();
    public native long nativeGetUnlockSecondsRemaining();
    public native boolean nativePurchaseSocialPass();

    private SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    // Core System Structural Navigation Framework Views
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#";
    private static final int KEY_LENGTH = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Window attributes must be requested BEFORE setting content view
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, RelentlessAdminReceiver.class);
        prefs = getSharedPreferences("relentless", MODE_PRIVATE);

        if (!hasUsageAccess()) {
            Toast.makeText(this, "Enable Usage Access for Relentless.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            finish();
            return;
        }

        if (!hasAccessibilityPermission()) {
            Toast.makeText(this, "Enable Accessibility for Relentless.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }

        // Generate master password on first app launch
        if (!prefs.contains("key_hash")) {
            String key = generateKey();
            prefs.edit().putString("key_hash", hash(key)).apply();
            showKeyOnce(key);
        }

        // Request device admin privileges if dropped
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Relentless needs admin to prevent uninstall. Requires 30-char key to remove.");
            startActivity(intent);
        }

        setContentView(R.layout.activity_main);

        // Initialize Workspace Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize System Navigation Framework
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Wire Up Hamburger Bar Toggle
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Handle Sidebar Item Execution
        // Handle Sidebar Item Execution
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                } else if (id == R.id.nav_device_admin) {
                    openDeviceAdminSettings();
                } else if (id == R.id.nav_help) {
                    showHelpDialog();
                } else if (id == R.id.nav_about) {
                    showAboutDialog();
                } else if (id == R.id.nav_revoke_admin) {
                    showRevokeDialog();
                }

                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        // Parse Live Admin State to Drawer Header Subtitle
        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            TextView headerSubtitle = headerView.findViewById(R.id.header_subtitle);
            if (headerSubtitle != null) {
                if (dpm.isAdminActive(adminComponent)) {
                    headerSubtitle.setText("Device Admin Active ✓");
                    headerSubtitle.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    headerSubtitle.setText("Device Admin Not Active");
                    headerSubtitle.setTextColor(Color.parseColor("#E57373"));
                }
            }
        }

        // Initialize Core Fragment Host
        setupBottomNavigation();

        // Spin up the core monitoring context
        startForegroundService(new Intent(this, BlockerService.class));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // Commit view layout to Workspace Dashboard Fragment frame dynamically on boot
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment())
                .commit();

        bottomNav.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int id = item.getItemId();

                if (id == R.id.navigation_dashboard) {
                    selectedFragment = new DashboardFragment();
                    if (toolbar != null) toolbar.setTitle("Dashboard");
                } else if (id == R.id.navigation_locked_apps) {
                    selectedFragment = new LockedAppsFragment(); // Swaps out smoothly to Locked Apps
                    if (toolbar != null) toolbar.setTitle("Policy Restrictions");
                } else if (id == R.id.navigation_statistics) {
                    selectedFragment = new StatisticsFragment(); // Swaps out smoothly to Telemetry Data
                    if (toolbar != null) toolbar.setTitle("Core Telemetry Data");
                }

                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                }

                return true;
            }
        });
    }

    // Navigation Drawer Helper Methods
    private void showBlockedAppsDialog() {
        Set<String> browsers = prefs.getStringSet("extra_browsers", new HashSet<>());
        StringBuilder message = new StringBuilder("Hard Blocked Apps:\n");
        for (String app : browsers) {
            message.append("• ").append(app).append("\n");
        }
        if (browsers.isEmpty()) {
            message.append("No custom blocked apps");
        }

        new AlertDialog.Builder(this)
                .setTitle("Blocked Applications")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showCreditSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_credit_settings, null);
        TextView tvCurrentCredits = dialogView.findViewById(R.id.tvCurrentCredits);
        if (tvCurrentCredits != null) {
            tvCurrentCredits.setText(String.valueOf(nativeGetCredits()));
        }

        new AlertDialog.Builder(this)
                .setTitle("Credit Settings")
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openDeviceAdminSettings() {
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Relentless needs admin to prevent uninstall. Requires 30-char key to remove.");
            startActivity(intent);
        } else {
            Toast.makeText(this, "Device admin is already active", Toast.LENGTH_SHORT).show();
        }
    }

    public void showStatisticsDialog() {
        long unlockTime = nativeGetUnlockSecondsRemaining();
        String status = unlockTime > 0 ?
                "Unlocked for " + (unlockTime / 60) + " more minutes" :
                "No active unlock";

        new AlertDialog.Builder(this)
                .setTitle("Statistics")
                .setMessage("Today's Credits: " + nativeGetCredits() + "\n" +
                        "Admin Active: " + dpm.isAdminActive(adminComponent) + "\n" +
                        "Status: " + status)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("How to Use Relentless")
                .setMessage("• Add browser packages to hard block them\n" +
                        "• Social apps require credits to unlock\n" +
                        "• You get 100 credits daily (resets at midnight)\n" +
                        "• Spend 50 credits for 30 minutes of access\n" +
                        "• The master key is required to remove admin access\n" +
                        "• Save your master key somewhere safe!\n" +
                        "• Enable Accessibility and Usage Access for full functionality")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About Relentless")
                .setMessage("Version 1.0\n\n" +
                        "A powerful device admin app to help you stay focused\n" +
                        "by blocking distracting apps and managing screen time.\n\n" +
                        "Features:\n" +
                        "• Hard block browsers\n" +
                        "• Credit-gated social apps\n" +
                        "• Daily credit system\n" +
                        "• Admin protection against uninstall\n" +
                        "• 30-character master key security")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showKeyOnce(String key) {
        new AlertDialog.Builder(this)
                .setTitle("YOUR MASTER KEY — COPY IT NOW")
                .setMessage("This is shown exactly once. Store it somewhere safe.\n\n" + key + "\n\nThis key is required to remove Relentless as a device admin.")
                .setCancelable(false)
                .setPositiveButton("I have saved it", null)
                .show();
    }

    public void showRevokeDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter 30-character master key");
        input.setHintTextColor(Color.GRAY);

        new AlertDialog.Builder(this)
                .setTitle("Revoke Admin Access")
                .setMessage("Enter your master key to remove Relentless as device admin (required before uninstall).")
                .setView(input)
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String entered = input.getText().toString();
                        String storedHash = prefs.getString("key_hash", "");
                        if (hash(entered).equals(storedHash)) {
                            dpm.removeActiveAdmin(adminComponent);
                            Toast.makeText(MainActivity.this, "Admin revoked. You can now uninstall.", Toast.LENGTH_LONG).show();

                            View headerView = navigationView.getHeaderView(0);
                            if (headerView != null) {
                                TextView headerSubtitle = headerView.findViewById(R.id.header_subtitle);
                                if (headerSubtitle != null) {
                                    headerSubtitle.setText("Device Admin Revoked");
                                    headerSubtitle.setTextColor(Color.parseColor("#E57373"));
                                }
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Wrong key. Access denied.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String generateKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private boolean hasAccessibilityPermission() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean hasUsageAccess() {
        AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}