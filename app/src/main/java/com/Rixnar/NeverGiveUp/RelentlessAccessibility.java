package com.Rixnar.NeverGiveUp;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

public class RelentlessAccessibility extends AccessibilityService {

    static { System.loadLibrary("rixnar"); }
    public native int checkAppStatus(String packageName);

    private long lastLoadTime = 0;
    private static final long LOAD_COOLDOWN = 10000;

    @Override
    public void onServiceConnected() {
        // Load initial state
        PersistenceHelper.loadFromInternalStorage(this);
        lastLoadTime = System.currentTimeMillis();
        
        // Ensure the monitor service is also running
        try {
            startForegroundService(new Intent(this, BlockerService.class));
        } catch (Exception e) {
            Log.e("RelentlessAcc", "Failed to start BlockerService", e);
        }
    }

    private long lastGateShownTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleAccessibilityEvent(event);
        } catch (Exception e) {
            Log.e("RelentlessAcc", "Error handling accessibility event", e);
        }
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (event.getPackageName() == null) return;
        
        String pkg = event.getPackageName().toString();
        
        // Internal state sync
        if (System.currentTimeMillis() - lastLoadTime > LOAD_COOLDOWN) {
            PersistenceHelper.loadFromInternalStorage(this);
            lastLoadTime = System.currentTimeMillis();
        }

        SharedPreferences prefs = getSharedPreferences("relentless", MODE_PRIVATE);

        // Logic for "activate only after user exits the first screen"
        boolean setupLocked = prefs.getBoolean("accessibility_setup_locked", false);
        if (!setupLocked) {
            // Check if user has left the settings app OR is now in Rixnar
            if (!pkg.contains("settings") && !pkg.equals(getPackageName())) {
                prefs.edit().putBoolean("accessibility_setup_locked", true).apply();
                PersistenceHelper.saveToInternalStorage(this);
                Log.i("RelentlessAcc", "User exited settings. Accessibility protection is now ARMED.");
            }
            // While in initial setup, do not block anything in settings to allow activation.
            if (pkg.contains("settings")) return;
        }

        if (pkg.equals(getPackageName())) return;

        // 1. Functional "Lock Device Admin Settings" logic
        if (pkg.contains("settings")) {
            if (prefs.getBoolean("settings_lock_admin", true)) {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName adminComponent = new android.content.ComponentName(this, RelentlessAdminReceiver.class);

                // ONLY block if admin is ALREADY active. This allows the initial activation.
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    CharSequence className = event.getClassName();
                    String eventText = event.getText().toString().toLowerCase();
                    
                    if (className != null) {
                        String cls = className.toString().toLowerCase();
                        // If it's a device admin, accessibility, or force-stop related screen, block it.
                        if (cls.contains("deviceadmin") || 
                            cls.contains("policy") || 
                            cls.contains("security") ||
                            cls.contains("accessibility") ||
                            cls.contains("forcedisplay") ||
                            cls.contains("forcestop") ||
                            (cls.contains("settings") && (eventText.contains("rixnar") || eventText.contains("relentless")))) {
                            
                            goHome();
                            Log.i("RelentlessAcc", "Blocked access to settings to prevent service removal.");
                            return;
                        }
                    }

                    // Secondary check: if the screen title/content mentions the app and we are in settings
                    if (eventText.contains("rixnar") || eventText.contains("relentless")) {
                        goHome();
                        Log.i("RelentlessAcc", "Blocked access to app-specific setting toggle.");
                        return;
                    }
                }
            }
            // If it's settings but not the deactivation screen, we fall through to the normal check
            // so that if the user explicitly blocked "Settings", it will work.
        }

        // 2. Whitelist other critical system components from any blocking
        if (pkg.contains("packageinstaller") || pkg.contains("permissioncontroller") || pkg.equals("com.android.systemui")) {
            return;
        }

        int status = checkAppStatus(pkg);
        if (prefs.getStringSet("extra_browsers", new HashSet<>()).contains(pkg)) {
            status = 2;
        } else if (status == 0 && prefs.getStringSet("extra_social", new HashSet<>()).contains(pkg)) {
            status = 1;
        }

        if (status == 2) {
            goHome();
        } else if (status == 1) {
            if (!BlockerService.isSocialUnlocked(this)) {
                if (android.os.SystemClock.elapsedRealtime() - lastGateShownTime > 3000) {
                    Intent i = new Intent(this, CreditGateActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    lastGateShownTime = android.os.SystemClock.elapsedRealtime();
                }
            }
        }
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {}
}
