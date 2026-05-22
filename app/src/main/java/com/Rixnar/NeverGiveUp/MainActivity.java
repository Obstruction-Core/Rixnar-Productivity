package com.Rixnar.NeverGiveUp;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.core.splashscreen.SplashScreen;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import android.content.DialogInterface;
import android.view.Window;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    static { System.loadLibrary("rixnar"); }

    private SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private BackupManager backupManager;
    private AlertDialog activeDisclosure;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#";
    private static final int KEY_LENGTH = 30;

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install Splash Screen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        // Auto-detect and follow system theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        super.onCreate(savedInstanceState);

        // Global crash protection
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e("RelentlessMain", "FATAL CRASH in main process", e);
            System.exit(1);
        });

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        enableFullscreen();

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, RelentlessAdminReceiver.class);
        PersistenceHelper.loadFromInternalStorage(this);

        prefs = getSharedPreferences("relentless", MODE_PRIVATE);
        backupManager = new BackupManager(this);

        boolean onboardingComplete = prefs.getBoolean("onboarding_complete", false);
        if (!onboardingComplete) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        setupFilePickers();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        applyEdgeToEdgeInsets();

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_device_admin) {
                openDeviceAdminSettings();
            } else if (id == R.id.nav_settings) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, new SettingsFragment())
                        .commit();
                if (toolbar != null) toolbar.setTitle("General Settings");
            } else if (id == R.id.nav_export) {
                exportBackup();
            } else if (id == R.id.nav_import) {
                importBackup();
            } else if (id == R.id.nav_help) {
                showHelpDialog();
            } else if (id == R.id.nav_support_dev) {
                launchUPIPayment();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            TextView headerSubtitle = headerView.findViewById(R.id.header_subtitle);
            if (headerSubtitle != null) {
                if (dpm.isAdminActive(adminComponent)) {
                    headerSubtitle.setText("Device Admin Active ✓");
                    headerSubtitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_green));
                } else {
                    headerSubtitle.setText("Device Admin Not Active");
                    headerSubtitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_red));
                }
            }
        }

        setupBottomNavigation();

        if (prefs.getInt("native_credits", 0) <= 0) {
            prefs.edit().putInt("native_credits", 100).apply();
            PersistenceHelper.saveToInternalStorage(this);
        }

        // --- GOOGLE PLAY COMPLIANCE: PROMINENT DISCLOSURE ---
        // checkAndRequestPermissions() moved to onResume to avoid duplicates

        startForegroundService(new Intent(this, BlockerService.class));
        scheduleWatchdog();
        scheduleBackupReminder();
        maybeRequestIgnoreBatteryOptimizations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume permission flow if we were waiting for user to return from settings
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (activeDisclosure != null && activeDisclosure.isShowing()) return;

        if (!hasUsageAccess()) {
            showUsageAccessDisclosure();
        } else if (!hasAccessibilityPermission()) {
            showAccessibilityDisclosure();
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !hasOverlayPermission()) {
            showOverlayDisclosure();
        } else if (!dpm.isAdminActive(adminComponent)) {
            showDeviceAdminDisclosure();
        }
    }

    private void showOverlayDisclosure() {
        activeDisclosure = new MaterialAlertDialogBuilder(this)
                .setTitle("Overlay Protection")
                .setMessage("Rixnar needs 'Display over other apps' permission to show the focus gate when you open a distracting app.\n\n" +
                        "This is required for robust operation on your Android version.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Maybe Later", null)
                .setCancelable(false)
                .show();
    }

    private void showUsageAccessDisclosure() {
        activeDisclosure = new MaterialAlertDialogBuilder(this)
                .setTitle("Usage Stats Required")
                .setMessage("Rixnar needs 'Usage Access' to know when browsers or social apps are opened, allowing it to apply your focus blocks automatically.\n\n" +
                        "Privacy: This data is processed only on your device.")
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                })
                .setNegativeButton("Exit App", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showAccessibilityDisclosure() {
        activeDisclosure = new MaterialAlertDialogBuilder(this)
                .setTitle("Accessibility Protection")
                .setMessage("To ensure focus mode cannot be easily bypassed, Rixnar uses an Accessibility Service to monitor and block distracting content.\n\n" +
                        "• Requirement: This is a core part of the app blocking functionality.\n" +
                        "• Privacy: We never collect personal data or monitor private messages.\n" +
                        "• Step: Find 'Rixnar' in the list and switch it to 'On'.")
                .setPositiveButton("Enable Service", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNegativeButton("Maybe Later", null)
                .setCancelable(false)
                .show();
    }

    private void showDeviceAdminDisclosure() {
        activeDisclosure = new MaterialAlertDialogBuilder(this)
                .setTitle("Admin Protection")
                .setMessage("Device Administrator permission is required to prevent unauthorized uninstallation of Rixnar during a focus period.\n\n" +
                        "Once activated, you can only remove the app by using your Master Key in the settings.\n\n" +
                        "Please click 'Activate' on the following screen.")
                .setPositiveButton("Enable Admin", (dialog, which) -> {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Rixnar needs admin to prevent uninstall. Requires 30-char key to remove.");
                    startActivity(intent);
                })
                .setNegativeButton("Maybe Later", null)
                .setCancelable(false)
                .show();
    }

    private void setupFilePickers() {
        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) exportToUri(uri);
                }
            }
        );

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importFromUri(uri);
                }
            }
        );
    }

    private void exportBackup() {
        new AlertDialog.Builder(this)
            .setTitle("Export Backup")
            .setMessage("This will create a signed backup of your key, streaks, and blocked apps. \n\nNote: Daily credits are not included in backups to prevent exploits. Continue?")
            .setPositiveButton("Export", (d, w) -> {
                try {
                    String backup = backupManager.exportToString();
                    File exportFile = new File(getCacheDir(), "rixnar_backup.r");
                    FileWriter fw = new FileWriter(exportFile);
                    fw.write(backup);
                    fw.close();

                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", exportFile);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, "rixnar_backup.r");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    exportLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportToUri(Uri uri) {
        try {
            String backup = backupManager.exportToString();
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(backup.getBytes("UTF-8"));
                os.close();
                prefs.edit().putLong("last_backup_export", System.currentTimeMillis()).apply();
                Toast.makeText(this, "Backup exported successfully!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup() {
        new AlertDialog.Builder(this)
            .setTitle("Import Backup")
            .setMessage("WARNING: This will overwrite your current settings. Continue?")
            .setPositiveButton("Select File", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                importLauncher.launch(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void importFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String inputLine;
                while ((inputLine = reader.readLine()) != null) sb.append(inputLine);
                reader.close();
                is.close();

                if (backupManager.importFromString(sb.toString())) {
                    PersistenceHelper.saveToInternalStorage(this);
                    Toast.makeText(this, "Restarting...", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleWatchdog() {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    ServiceWatchdogWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "relentless_service_watchdog", ExistingPeriodicWorkPolicy.UPDATE, req);
        } catch (Exception ignored) {}
    }

    private void scheduleBackupReminder() {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    BackupReminderWorker.class, 24, TimeUnit.HOURS).build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "relentless_backup_reminder", ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (Exception ignored) {}
    }

    private void maybeRequestIgnoreBatteryOptimizations() {
        try {
            boolean asked = prefs.getBoolean("asked_ignore_battery_optimizations", false);
            if (asked) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
            prefs.edit().putBoolean("asked_ignore_battery_optimizations", true).apply();
        } catch (Exception ignored) {}
    }

    private void applyEdgeToEdgeInsets() {
        final View bottomNav = findViewById(R.id.bottom_navigation);
        if (toolbar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
                int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                return insets;
            });
        }
    }

    private void enableFullscreen() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Do not hide status bars here if the user wants them visible but themed.
        // If they want them hidden, keep it.
        // Based on the "blue bar" request, it's likely they are visible but blue.
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment()).commit();

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();
            if (id == R.id.navigation_dashboard) {
                selectedFragment = new DashboardFragment();
                if (toolbar != null) toolbar.setTitle("Console");
            } else if (id == R.id.navigation_locked_apps) {
                selectedFragment = new LockedAppsFragment();
                if (toolbar != null) toolbar.setTitle("Policies");
            } else if (id == R.id.navigation_statistics) {
                selectedFragment = new StatisticsFragment();
                if (toolbar != null) toolbar.setTitle("Performance");
            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, selectedFragment).commit();
            }
            return true;
        });
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("How to Use Rixnar")
                .setMessage("• Add browser packages to hard block them\n" +
                        "• Social apps require credits to unlock\n" +
                        "• You get 100 credits daily (resets at midnight)\n" +
                        "• Spend 50 credits for 30 minutes of access\n" +
                        "• The master key is required to remove admin access")
                .setPositiveButton("Got it", null).show();
    }

    private void launchUPIPayment() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Support Relentless")
                .setMessage("A quick honest request: If this app helps you stay focused, a small contribution of $1 / £1 / ₹100 goes a long way in keeping development alive.\n\nThank you for being part of the journey!")
                .setPositiveButton("Support with GPay", (dialog, which) -> {
                    // Actual UPI Payment logic
                    String upiId = BuildConfig.UPI_ID;
                    String name = "Relentless Developer";
                    String note = "Support Relentless Development";

                    Uri uri = Uri.parse("upi://pay").buildUpon()
                            .appendQueryParameter("pa", upiId)
                            .appendQueryParameter("pn", name)
                            .appendQueryParameter("tn", note)
                            .appendQueryParameter("cu", "INR")
                            .build();

                    Intent upiPayIntent = new Intent(Intent.ACTION_VIEW);
                    upiPayIntent.setData(uri);

                    Intent chooser = Intent.createChooser(upiPayIntent, "Pay with");
                    if (null != chooser.resolveActivity(getPackageManager())) {
                        startActivity(chooser);
                    } else {
                        Toast.makeText(this, "No UPI app found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Maybe Later", null)
                .show();
    }

    private void openDeviceAdminSettings() {
        if (!dpm.isAdminActive(adminComponent)) {
            showDeviceAdminDisclosure();
        } else {
            Toast.makeText(this, "Device admin is already active", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasAccessibilityPermission() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean hasUsageAccess() {
        AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(this);
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
        if (handler != null && refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }
}
