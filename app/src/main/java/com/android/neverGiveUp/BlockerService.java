package com.android.neverGiveUp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BlockerService extends Service {

    static { System.loadLibrary("relentless"); }

    public native int checkAppStatus(String packageName);
    public native void nativeResetDailyCredits();

    private static final String CHANNEL_ID = "relentless_channel";
    private static final String ACTION_MIDNIGHT_RESET = "com.android.neverGiveUp.MIDNIGHT_RESET";
    private static final String TAG = "RelentlessService";

    // Hardware verification anchors for tamper protection
    private long lastCheckedRealtime = SystemClock.elapsedRealtime();
    private String lastCheckedDateString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

    public static volatile boolean socialUnlocked = false;
    public static volatile long unlockExpiryMs = 0;

    private BroadcastReceiver midnightReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MIDNIGHT_RESET.equals(intent.getAction())) {
                // Double check validity before accepting raw AlarmManager wake up
                verifySystemTimeIntegrity();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Relentless")
                .setContentText("Focus mode active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
        startForeground(1, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(midnightReceiver, new IntentFilter(ACTION_MIDNIGHT_RESET), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(midnightReceiver, new IntentFilter(ACTION_MIDNIGHT_RESET));
        }

        scheduleMidnightReset();
        new Thread(this::executionLoop).start();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Relentless", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Focus mode running");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private void executionLoop() {
        SharedPreferences prefs = getSharedPreferences("relentless", MODE_PRIVATE);
        String lastBlockedPkg = "";

        while (true) {
            // 1. Audit system time integrity on every cycle step to prevent credit/timer spoofing
            verifySystemTimeIntegrity();

            String pkg = getForegroundPackage();
            if (pkg != null && !pkg.isEmpty()) {
                int status = checkAppStatus(pkg);
                if (status == 0) {
                    Set<String> extraBrowsers = prefs.getStringSet("extra_browsers", new HashSet<>());
                    Set<String> extraSocial   = prefs.getStringSet("extra_social",   new HashSet<>());
                    if (extraBrowsers.contains(pkg)) status = 2;
                    else if (extraSocial.contains(pkg)) status = 1;
                }

                // If social pass is actively running and valid, downgrade social restriction status
                if (status == 1 && isSocialUnlocked()) {
                    status = 0;
                }

                if (status == 2) {
                    goHome();
                    if (!pkg.equals(lastBlockedPkg)) {
                        lastBlockedPkg = pkg;
                        for (int i = 0; i < 3; i++) {
                            goHome();
                            try { Thread.sleep(80); } catch (InterruptedException e) { break; }
                        }
                    }
                } else if (status == 1) {
                    showCreditGate();
                    lastBlockedPkg = "";
                } else {
                    lastBlockedPkg = "";
                }
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
        }
    }

    /**
     * Inspects monotonic CPU hardware ticks against system wall clock time.
     * Prevents users from manually changing dates to spoof midnights or extend session timeouts.
     */
    private void verifySystemTimeIntegrity() {
        long currentRealtime = SystemClock.elapsedRealtime();
        String currentDateString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        long elapsedRealtimeDeltaMs = currentRealtime - lastCheckedRealtime;

        // If the calendar date changed in device settings
        if (!currentDateString.equals(lastCheckedDateString)) {
            // If the user skipped dates, but the hardware mono-clock shows only short moments passed,
            // a clear device time modification attempt has occurred.
            if (elapsedRealtimeDeltaMs < 45000000) { // Safety threshold: ~12.5 hours of continuous real run-time
                Log.w(TAG, "Time anomaly intercepted. Manual date modifications blocked.");
                // Retain old tracking baseline string to freeze daily credit allocation
                return;
            }

            // Real, untampered midnight transition confirmed via continuous execution hardware ticks
            Log.i(TAG, "Valid midnight boundary crossed. Executing hardware-verified credit reset.");
            nativeResetDailyCredits();
            lastCheckedDateString = currentDateString;
        }

        lastCheckedRealtime = currentRealtime;
    }

    private String getForegroundPackage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 5000, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String pkg = "";
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                pkg = event.getPackageName();
            }
        }
        return pkg;
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void showCreditGate() {
        Intent i = new Intent(this, CreditGateActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void scheduleMidnightReset() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_MIDNIGHT_RESET);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.add(Calendar.DAY_OF_MONTH, 1);

        if (am != null) {
            am.setRepeating(AlarmManager.RTC_WAKEUP,
                    midnight.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        }
    }

    public static boolean isSocialUnlocked() {
        // Fallback constraint validation safety check
        if (socialUnlocked && System.currentTimeMillis() > unlockExpiryMs) {
            socialUnlocked = false;
        }
        return socialUnlocked;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(midnightReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        super.onTimeout(startId, fgsType);
        stopSelf(startId);
        startForegroundService(new Intent(this, BlockerService.class));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}