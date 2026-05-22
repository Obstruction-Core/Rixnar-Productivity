package com.Rixnar.NeverGiveUp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        // Note: this will NOT run after user Force Stop (Android blocks restarts until user opens app).
        try {
            ContextCompat.startForegroundService(context, new Intent(context, BlockerService.class));
        } catch (Exception ignored) {}
    }
}

