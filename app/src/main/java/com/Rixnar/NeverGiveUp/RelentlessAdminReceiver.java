package com.Rixnar.NeverGiveUp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class RelentlessAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Rixnar admin active. You're locked in.", Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Enter your 30-character key in Rixnar to disable admin.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Rixnar admin disabled.", Toast.LENGTH_SHORT).show();
    }
}
