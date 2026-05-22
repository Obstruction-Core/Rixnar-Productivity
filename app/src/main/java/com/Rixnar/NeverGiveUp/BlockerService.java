package com.Rixnar.NeverGiveUp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.pm.ServiceInfo;
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

    static { System.loadLibrary("rixnar"); }

    public native int checkAppStatus(String packageName);

    private static final String CHANNEL_ID = "rixnar_channel";
    private static final String ACTION_MIDNIGHT_RESET = "com.Rixnar.NeverGiveUp.MIDNIGHT_RESET";
    private static final String TAG = "RelentlessService";

    private long lastCheckedRealtime = SystemClock.elapsedRealtime();
    private String lastCheckedDateString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    private SharedPreferences prefs;
    private static final String PREF_LAST_CREDIT_RESET_DATE = "last_credit_reset_date_string";

    private volatile boolean loopRunning = false;
    private Thread loopThread;

    private BroadcastReceiver midnightReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MIDNIGHT_RESET.equals(intent.getAction())) {
                verifySystemTimeIntegrity();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Set crash handler for the :guardian process
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e(TAG, "FATAL CRASH in guardian process", e);
            System.exit(1);
        });

        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Rixnar")
                .setContentText("Focus mode active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }

        PersistenceHelper.loadFromInternalStorage(this);
        prefs = getSharedPreferences("relentless", MODE_PRIVATE);
        
        // Repair logic: ensure credits are 100 if they were stuck at 0
        if (prefs.getInt("native_credits", 0) <= 0) {
            prefs.edit().putInt("native_credits", 100).apply();
            PersistenceHelper.saveToInternalStorage(this);
        }
        
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String stored = prefs.getString(PREF_LAST_CREDIT_RESET_DATE, null);
        if (stored == null || stored.trim().isEmpty()) {
            prefs.edit().putString(PREF_LAST_CREDIT_RESET_DATE, today).commit();
            stored = today;
        }
        lastCheckedDateString = stored;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(midnightReceiver, new IntentFilter(ACTION_MIDNIGHT_RESET), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(midnightReceiver, new IntentFilter(ACTION_MIDNIGHT_RESET));
        }

        scheduleMidnightReset();
        startExecutionLoopIfNeeded();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Attempt to restart the service if swiped away
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
            getApplicationContext(), 1, restartServiceIntent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null) {
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
        }
        
        super.onTaskRemoved(rootIntent);
    }

    private synchronized void startExecutionLoopIfNeeded() {
        if (loopThread != null && loopThread.isAlive()) return;
        loopRunning = true;
        loopThread = new Thread(this::executionLoop);
        loopThread.setName("Relentless-ExecutionLoop");
        loopThread.start();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Rixnar", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Focus mode running");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private long lastGateShownTime = 0;

    private void executionLoop() {
        String lastBlockedPkg = "";

        while (loopRunning) {
            verifySystemTimeIntegrity();

            String pkg = getForegroundPackage();
            if (pkg != null && !pkg.isEmpty()) {
                // BUG FIX: Ignore our own app, settings, and common system components
                if (pkg.equals(getPackageName()) || 
                    pkg.contains("settings") || 
                    pkg.contains("packageinstaller") ||
                    pkg.contains("permissioncontroller") ||
                    pkg.equals("com.android.systemui") ||
                    pkg.contains("launcher") || 
                    pkg.contains("trebuchet")) {
                    
                    lastBlockedPkg = "";
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    continue;
                }

                int status = checkAppStatus(pkg);
                if (prefs.getStringSet("extra_browsers", new HashSet<>()).contains(pkg)) {
                    status = 2;
                } else if (status == 0 && prefs.getStringSet("extra_social", new HashSet<>()).contains(pkg)) {
                    status = 1;
                }

                if (status == 1 && isSocialUnlocked(this)) {
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
                    // Prevent rapid restarting of the gate activity
                    if (SystemClock.elapsedRealtime() - lastGateShownTime > 3000) {
                        showCreditGate();
                        lastGateShownTime = SystemClock.elapsedRealtime();
                    }
                    lastBlockedPkg = "";
                } else {
                    lastBlockedPkg = "";
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { break; }
        }
    }

    private void verifySystemTimeIntegrity() {
        long currentRealtime = SystemClock.elapsedRealtime();
        String currentDateString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        long elapsedRealtimeDeltaMs = currentRealtime - lastCheckedRealtime;

        if (!currentDateString.equals(lastCheckedDateString)) {
            if (elapsedRealtimeDeltaMs < 5 * 60 * 1000) return;

            Log.i(TAG, "Day boundary crossed. Executing daily credit reset.");
            prefs.edit()
                .putInt("native_credits", 100)
                .putBoolean("social_unlocked", false)
                .putString(PREF_LAST_CREDIT_RESET_DATE, currentDateString)
                .apply();
            PersistenceHelper.saveToInternalStorage(this);
            lastCheckedDateString = currentDateString;
        }
        lastCheckedRealtime = currentRealtime;
    }

    public static boolean isSocialUnlocked(Context context) {
        SharedPreferences lp = context.getSharedPreferences("relentless", MODE_PRIVATE);
        boolean unlocked = lp.getBoolean("social_unlocked", false);
        long expiry = lp.getLong("social_unlock_expiry", 0);
        
        if (unlocked && System.currentTimeMillis() > expiry) {
            lp.edit().putBoolean("social_unlocked", false).apply();
            PersistenceHelper.saveToInternalStorage(context);
            return false;
        }
        return unlocked;
    }

    private String getForegroundPackage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return "";
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
        if (am == null) return;
        Intent i = new Intent(ACTION_MIDNIGHT_RESET);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.add(Calendar.DAY_OF_MONTH, 1);

        am.setRepeating(AlarmManager.RTC_WAKEUP,
                midnight.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loopRunning = false;
        try { unregisterReceiver(midnightReceiver); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
