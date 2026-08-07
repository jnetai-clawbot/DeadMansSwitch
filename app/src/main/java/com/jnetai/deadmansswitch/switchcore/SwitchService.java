package com.jnetai.deadmansswitch.switchcore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import com.jnetai.deadmansswitch.MainActivity;
import com.jnetai.deadmansswitch.R;
import com.jnetai.deadmansswitch.utils.ErrorHandler;
import com.jnetai.deadmansswitch.utils.DebugLogger;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SwitchService extends Service {

    private static final String TAG = "SwitchService";
    private static final String CHANNEL_ID = "deadmans_switch";
    private static final int NOTIFICATION_ID = 4001;
    private static final long CHECK_INTERVAL_MS = 60000;

    private SwitchStorage storage;
    private Handler handler;
    private Runnable checkRunnable;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        storage = new SwitchStorage(this);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        DebugLogger.log(TAG, "SwitchService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                DebugLogger.log(TAG, "onStartCommand with null intent");
                return START_STICKY;
            }

            String action = intent.getAction();
            DebugLogger.log(TAG, "onStartCommand action: " + action);

            switch (action) {
                case "ARM":
                    startMonitoring();
                    break;
                case "DISARM":
                    stopMonitoring();
                    break;
                case "CHECKIN":
                    break;
                case "TEST_ACTIONS":
                    triggerEmergencyActions();
                    break;
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-001", "Service command failed", e, null);
        }

        return START_STICKY;
    }

    private void startMonitoring() {
        if (running) {
            DebugLogger.log(TAG, "Already monitoring");
            return;
        }

        running = true;
        startForeground(NOTIFICATION_ID, buildNotification("Dead Man's Switch ARMED"));

        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                checkDeadline();
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };

        handler.post(checkRunnable);
        DebugLogger.log(TAG, "Monitoring started");
    }

    private void checkDeadline() {
        try {
            if (!storage.isArmed()) return;

            long lastCheckin = storage.getLastCheckin();
            int intervalHours = storage.getIntervalHours();
            long deadline = lastCheckin + (intervalHours * 3600000L);

            if (System.currentTimeMillis() >= deadline) {
                DebugLogger.log(TAG, "DEADLINE EXCEEDED! Triggering emergency actions.");
                storage.addLogEntry("DEADLINE EXCEEDED at " +
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                triggerEmergencyActions();
                storage.setArmed(false);
                stopMonitoring();
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-002", "Deadline check failed", e, null);
        }
    }

    private void triggerEmergencyActions() {
        try {
            String contact = storage.getEmergencyContact();
            String message = storage.getEmergencyMessage();
            String webhook = storage.getWebhookUrl();

            DebugLogger.log(TAG, "Triggering emergency actions");

            if (!contact.isEmpty() && !message.isEmpty()) {
                sendEmergencySMS(contact, message);
            }

            if (!webhook.isEmpty()) {
                sendWebhook(webhook);
            }

            sendLocalNotification("EMERGENCY ACTIONS TRIGGERED",
                "Dead Man's Switch activated. Contact: " + contact);

            storage.addLogEntry("Emergency actions triggered at " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-003", "Emergency actions failed", e, null);
        }
    }

    private void sendEmergencySMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            DebugLogger.log(TAG, "Emergency SMS sent to " + phoneNumber);
            storage.addLogEntry("SMS sent to " + phoneNumber);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-004", "Failed to send SMS", e, null);
            storage.addLogEntry("SMS FAILED: " + e.getMessage());
        }
    }

    private void sendWebhook(String webhookUrl) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String jsonPayload = "{\"event\":\"dead_mans_switch\"," +
                "\"message\":\"" + storage.getEmergencyMessage() + "\"," +
                "\"timestamp\":" + System.currentTimeMillis() + "}";

            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            DebugLogger.log(TAG, "Webhook sent, response: " + responseCode);
            storage.addLogEntry("Webhook sent (HTTP " + responseCode + ")");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-005", "Failed to send webhook", e, null);
            storage.addLogEntry("Webhook FAILED: " + e.getMessage());
        }
    }

    private void sendLocalNotification(String title, String message) {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

            manager.notify((int) System.currentTimeMillis(), notification);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-SRV-006", "Failed to send local notification", e, null);
        }
    }

    private void stopMonitoring() {
        running = false;
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
        stopForeground(true);
        stopSelf();
        DebugLogger.log(TAG, "Monitoring stopped");
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dead Man's Switch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Dead Man's Switch",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Dead Man's Switch monitoring notification");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }
}
