package com.Rixnar.NeverGiveUp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BackupReminderWorker extends Worker {

    private static final String CHANNEL_ID = "backup_reminder_channel";
    private static final int NOTIFICATION_ID = 1001;

    public BackupReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("relentless", Context.MODE_PRIVATE);
            
            // Respect user preference
            if (!prefs.getBoolean("settings_backup_reminder", true)) {
                return Result.success();
            }

            long lastExport = prefs.getLong("last_backup_export", 0);
            long now = System.currentTimeMillis();
            
            // Show reminder if no export in last 7 days
            long sevenDaysMs = 7 * 24 * 60 * 60 * 1000L;
            if (now - lastExport > sevenDaysMs) {
                showBackupReminder();
            }
            
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    private void showBackupReminder() {
        Context context = getApplicationContext();
        
        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Backup Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminds you to backup your Relentless data");
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        // Create intent to open MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Backup Your Data")
            .setContentText("It's been over 7 days since your last backup. Export now to save your progress!")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("It's been over 7 days since your last backup. Export now to save your progress and protect your focus data!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Notification permission not granted
        }
    }
}
