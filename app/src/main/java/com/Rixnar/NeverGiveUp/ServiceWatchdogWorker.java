package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ServiceWatchdogWorker extends Worker {

    public ServiceWatchdogWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Best-effort: ensure service is running. Starting repeatedly is safe because BlockerService
        // guards against spawning multiple loops.
        try {
            ContextCompat.startForegroundService(getApplicationContext(),
                    new Intent(getApplicationContext(), BlockerService.class));
        } catch (Exception ignored) {}
        return Result.success();
    }
}

