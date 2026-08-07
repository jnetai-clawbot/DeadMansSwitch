package com.jnetai.deadmansswitch.switchcore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.jnetai.deadmansswitch.utils.DebugLogger;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                DebugLogger.log(TAG, "Boot completed, checking if switch was armed");

                SwitchStorage storage = new SwitchStorage(context);
                if (storage.isArmed()) {
                    DebugLogger.log(TAG, "Switch was armed, restarting service");
                    Intent serviceIntent = new Intent(context, SwitchService.class);
                    serviceIntent.setAction("ARM");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                }
            }
        } catch (Exception e) {
            DebugLogger.log(TAG, "BootReceiver error: " + e.getMessage());
        }
    }
}
