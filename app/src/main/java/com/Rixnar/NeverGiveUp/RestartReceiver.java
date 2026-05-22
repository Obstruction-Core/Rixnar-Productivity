package com.Rixnar.NeverGiveUp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

/**
 * Restarts the foreground protection service after the app task is removed (recents swipe)
 * or after scheduled watchdog alarms.
 *
 * Note: Android "Force stop" blocks receivers/alarms until the user manually opens the app again.
 */
public class RestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        try {
            ContextCompat.startForegroundService(context, new Intent(context, BlockerService.class));
        } catch (Exception ignored) {}
    }
}

