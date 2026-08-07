package com.jnetai.deadmansswitch.switchcore;

import android.content.Context;
import android.content.SharedPreferences;
import com.jnetai.deadmansswitch.utils.ErrorHandler;
import com.jnetai.deadmansswitch.utils.DebugLogger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SwitchStorage {

    private static final String TAG = "SwitchStorage";
    private static final String PREFS_NAME = "deadmans_switch_prefs";
    private static final String KEY_ARMED = "armed";
    private static final String KEY_LAST_CHECKIN = "last_checkin";
    private static final String KEY_INTERVAL_HOURS = "interval_hours";
    private static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    private static final String KEY_EMERGENCY_MESSAGE = "emergency_message";
    private static final String KEY_WEBHOOK_URL = "webhook_url";
    private static final String KEY_LOG = "log";

    private final SharedPreferences prefs;

    public SwitchStorage(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        DebugLogger.log(TAG, "SwitchStorage initialized");
    }

    public boolean isArmed() {
        return prefs.getBoolean(KEY_ARMED, false);
    }

    public void setArmed(boolean armed) {
        prefs.edit().putBoolean(KEY_ARMED, armed).apply();
    }

    public long getLastCheckin() {
        return prefs.getLong(KEY_LAST_CHECKIN, 0);
    }

    public void setLastCheckin(long timestamp) {
        prefs.edit().putLong(KEY_LAST_CHECKIN, timestamp).apply();
    }

    public int getIntervalHours() {
        return prefs.getInt(KEY_INTERVAL_HOURS, 24);
    }

    public void setIntervalHours(int hours) {
        prefs.edit().putInt(KEY_INTERVAL_HOURS, hours).apply();
    }

    public String getEmergencyContact() {
        return prefs.getString(KEY_EMERGENCY_CONTACT, "");
    }

    public void setEmergencyContact(String contact) {
        prefs.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply();
    }

    public String getEmergencyMessage() {
        return prefs.getString(KEY_EMERGENCY_MESSAGE, "Dead Man's Switch activated! Please check on me.");
    }

    public void setEmergencyMessage(String message) {
        prefs.edit().putString(KEY_EMERGENCY_MESSAGE, message).apply();
    }

    public String getWebhookUrl() {
        return prefs.getString(KEY_WEBHOOK_URL, "");
    }

    public void setWebhookUrl(String url) {
        prefs.edit().putString(KEY_WEBHOOK_URL, url).apply();
    }

    public void addLogEntry(String entry) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String logEntry = "[" + timestamp + "] " + entry + "\n";
            String existingLog = prefs.getString(KEY_LOG, "");
            String newLog = logEntry + existingLog;
            if (newLog.length() > 10000) {
                newLog = newLog.substring(0, 10000);
            }
            prefs.edit().putString(KEY_LOG, newLog).apply();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-STORE-001", "Failed to add log entry", e, null);
        }
    }

    public String getLog() {
        String log = prefs.getString(KEY_LOG, "");
        if (log.isEmpty()) {
            return "No log entries yet.";
        }
        return log;
    }

    public void clearLog() {
        prefs.edit().putString(KEY_LOG, "").apply();
    }
}
