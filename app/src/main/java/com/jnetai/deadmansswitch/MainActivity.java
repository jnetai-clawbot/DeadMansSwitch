package com.jnetai.deadmansswitch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.jnetai.deadmansswitch.switchcore.SwitchService;
import com.jnetai.deadmansswitch.switchcore.SwitchStorage;
import com.jnetai.deadmansswitch.utils.ErrorHandler;
import com.jnetai.deadmansswitch.utils.DebugLogger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private EditText etIntervalHours;
    private EditText etEmergencyContact;
    private EditText etEmergencyMessage;
    private EditText etWebhookUrl;
    private Button btnArm;
    private Button btnDisarm;
    private Button btnCheckIn;
    private Button btnTestActions;
    private Button btnAbout;
    private TextView tvStatus;
    private TextView tvNextCheckin;
    private TextView tvLog;
    private ProgressBar progressBar;
    private ScrollView scrollLog;

    private SwitchStorage storage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            DebugLogger.log(TAG, "MainActivity onCreate started");

            storage = new SwitchStorage(this);
            initViews();
            loadSettings();
            updateStatus();

            DebugLogger.log(TAG, "MainActivity onCreate completed");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-001", "Failed to initialize MainActivity", e, this);
        }
    }

    private void initViews() {
        try {
            etIntervalHours = findViewById(R.id.etIntervalHours);
            etEmergencyContact = findViewById(R.id.etEmergencyContact);
            etEmergencyMessage = findViewById(R.id.etEmergencyMessage);
            etWebhookUrl = findViewById(R.id.etWebhookUrl);
            btnArm = findViewById(R.id.btnArm);
            btnDisarm = findViewById(R.id.btnDisarm);
            btnCheckIn = findViewById(R.id.btnCheckIn);
            btnTestActions = findViewById(R.id.btnTestActions);
            btnAbout = findViewById(R.id.btnAbout);
            tvStatus = findViewById(R.id.tvStatus);
            tvNextCheckin = findViewById(R.id.tvNextCheckin);
            tvLog = findViewById(R.id.tvLog);
            progressBar = findViewById(R.id.progressBar);
            scrollLog = findViewById(R.id.scrollLog);

            btnArm.setOnClickListener(v -> armSwitch());
            btnDisarm.setOnClickListener(v -> disarmSwitch());
            btnCheckIn.setOnClickListener(v -> checkIn());
            btnTestActions.setOnClickListener(v -> testActions());
            btnAbout.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            });

            DebugLogger.log(TAG, "Views initialized successfully");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-002", "Failed to initialize views", e, this);
        }
    }

    private void loadSettings() {
        etIntervalHours.setText(String.valueOf(storage.getIntervalHours()));
        etEmergencyContact.setText(storage.getEmergencyContact());
        etEmergencyMessage.setText(storage.getEmergencyMessage());
        etWebhookUrl.setText(storage.getWebhookUrl());
    }

    private void saveSettings() {
        try {
            int hours = 24;
            try { hours = Integer.parseInt(etIntervalHours.getText().toString().trim()); } catch (NumberFormatException ignored) {}
            if (hours < 1) hours = 1;
            if (hours > 720) hours = 720;

            storage.setIntervalHours(hours);
            storage.setEmergencyContact(etEmergencyContact.getText().toString().trim());
            storage.setEmergencyMessage(etEmergencyMessage.getText().toString().trim());
            storage.setWebhookUrl(etWebhookUrl.getText().toString().trim());
            DebugLogger.log(TAG, "Settings saved");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-003", "Failed to save settings", e, this);
        }
    }

    private void armSwitch() {
        try {
            saveSettings();

            if (storage.getEmergencyContact().isEmpty() && storage.getWebhookUrl().isEmpty()) {
                Toast.makeText(this, "Set at least one emergency action (contact or webhook)", Toast.LENGTH_LONG).show();
                return;
            }

            Intent serviceIntent = new Intent(this, SwitchService.class);
            serviceIntent.setAction("ARM");
            startForegroundService(serviceIntent);

            storage.setArmed(true);
            storage.setLastCheckin(System.currentTimeMillis());
            updateStatus();
            DebugLogger.log(TAG, "Switch armed");
            Toast.makeText(this, "Dead Man's Switch ARMED", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-004", "Failed to arm switch", e, this);
        }
    }

    private void disarmSwitch() {
        try {
            Intent serviceIntent = new Intent(this, SwitchService.class);
            serviceIntent.setAction("DISARM");
            startService(serviceIntent);

            storage.setArmed(false);
            updateStatus();
            DebugLogger.log(TAG, "Switch disarmed");
            Toast.makeText(this, "Dead Man's Switch DISARMED", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-005", "Failed to disarm switch", e, this);
        }
    }

    private void checkIn() {
        try {
            if (!storage.isArmed()) {
                Toast.makeText(this, "Arm the switch first", Toast.LENGTH_SHORT).show();
                return;
            }

            storage.setLastCheckin(System.currentTimeMillis());
            storage.addLogEntry("Check-in successful at " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));

            Intent serviceIntent = new Intent(this, SwitchService.class);
            serviceIntent.setAction("CHECKIN");
            startService(serviceIntent);

            updateStatus();
            DebugLogger.log(TAG, "Check-in recorded");
            Toast.makeText(this, "Check-in recorded!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-006", "Failed to check in", e, this);
        }
    }

    private void testActions() {
        try {
            saveSettings();
            setLoading(true);
            DebugLogger.log(TAG, "Testing emergency actions");

            Intent serviceIntent = new Intent(this, SwitchService.class);
            serviceIntent.setAction("TEST_ACTIONS");
            startService(serviceIntent);

            new android.os.Handler().postDelayed(() -> {
                setLoading(false);
                Toast.makeText(this, "Test actions triggered. Check logs.", Toast.LENGTH_LONG).show();
            }, 2000);

        } catch (Exception e) {
            setLoading(false);
            ErrorHandler.handle(TAG, "ERR-MAIN-007", "Failed to test actions", e, this);
        }
    }

    private void updateStatus() {
        boolean armed = storage.isArmed();
        long lastCheckin = storage.getLastCheckin();
        int intervalHours = storage.getIntervalHours();

        if (armed) {
            tvStatus.setText("STATUS: ARMED");
            tvStatus.setTextColor(0xFF00FF00);

            long deadline = lastCheckin + (intervalHours * 3600000L);
            long remaining = deadline - System.currentTimeMillis();

            if (remaining > 0) {
                long hours = remaining / 3600000;
                long minutes = (remaining % 3600000) / 60000;
                tvNextCheckin.setText("Next check-in due in: " + hours + "h " + minutes + "m");
                tvNextCheckin.setTextColor(0xFFAAAAAA);
            } else {
                tvNextCheckin.setText("CHECK-IN OVERDUE! Emergency actions triggered!");
                tvNextCheckin.setTextColor(0xFFE94560);
            }
        } else {
            tvStatus.setText("STATUS: DISARMED");
            tvStatus.setTextColor(0xFFE94560);
            tvNextCheckin.setText("Arm the switch to begin monitoring");
            tvNextCheckin.setTextColor(0xFFAAAAAA);
        }

        btnArm.setEnabled(!armed);
        btnDisarm.setEnabled(armed);
        btnCheckIn.setEnabled(armed);

        tvLog.setText(storage.getLog());
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
