package com.vichrov.meizu21rgbring;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 1001;

    private IRingService ringService;
    private TextView status;
    private View preview;
    private SeekBar red, green, blue, brightness;
    private SeekBar awStrength, awAmplitude;
    private SeekBar ledIndex, ledBrightness;
    private TextView ledIndexLabel;

    private final Shizuku.UserServiceArgs userServiceArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", RingUserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb_ring")
            .tag("meizu21_rgb_ring")
            .version(6)
            .debuggable(true);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ringService = IRingService.Stub.asInterface(service);
            appendStatus("UserService connected.");
            runDiagnostics();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ringService = null;
            appendStatus("UserService disconnected.");
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        appendStatus("Shizuku binder received. UID=" + safeShizukuUid());
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        ringService = null;
        appendStatus("Shizuku binder died.");
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode != REQ_SHIZUKU) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            appendStatus("Shizuku access granted.");
            bindUserService();
        } else {
            appendStatus("Shizuku access denied.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        refreshShizukuStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, pad);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        root.addView(text("Meizu 21 RGB Ring", 26, true));
        TextView sub = text("v0.6 live LightState probe — stock Flyme effects + music light", 14, false);
        sub.setPadding(0, dp(4), 0, dp(18));
        root.addView(sub);

        Button grant = button("1. Grant / connect Shizuku");
        grant.setOnClickListener(v -> requestShizuku());
        root.addView(grant);

        Button diag = button("2. Run Lights API diagnostics");
        diag.setOnClickListener(v -> runDiagnostics());
        root.addView(diag);

        preview = new View(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        previewLp.setMargins(0, dp(18), 0, dp(12));
        root.addView(preview, previewLp);

        red = addSlider(root, "R", 255, 255);
        green = addSlider(root, "G", 255, 87);
        blue = addSlider(root, "B", 255, 87);
        brightness = addSlider(root, "High-level brightness (RGB scaling)", 255, 255);

        SeekBar.OnSeekBarChangeListener previewListener = new SimpleSeekListener(this::updatePreview);
        red.setOnSeekBarChangeListener(previewListener);
        green.setOnSeekBarChangeListener(previewListener);
        blue.setOnSeekBarChangeListener(previewListener);
        brightness.setOnSeekBarChangeListener(previewListener);
        updatePreview();

        Button apply = button("Apply selected color (whole ring)");
        apply.setOnClickListener(v -> call(service -> service.setPureColor(selectedColor(), brightness.getProgress())));
        root.addView(apply);

        Button nativeRainbow = button("Native smooth rainbow (whole ring)");
        nativeRainbow.setOnClickListener(v -> call(service -> service.setRainbow(255)));
        root.addView(nativeRainbow);

        Button softwareRainbow = button("Software rainbow (whole ring)");
        softwareRainbow.setOnClickListener(v -> call(service -> service.startSoftwareRainbow(brightness.getProgress())));
        root.addView(softwareRainbow);

        TextView awSection = text("Stock Flyme effects / music light", 20, true);
        awSection.setPadding(0, dp(24), 0, dp(8));
        root.addView(awSection);

        TextView awNote = text("Recovered from your stock Settings/framework. Effect 38 starts the exact light_preview_music playback path. While it is running, press diagnostics to sample the live LightState including brightnessArray, percent, playerId and semaphore letter.", 12, false);
        awNote.setPadding(0, 0, 0, dp(8));
        root.addView(awNote);

        Button awDiag = button("3. Stock Flyme diagnostics + live state probe");
        awDiag.setOnClickListener(v -> call(IRingService::awDiagnostics));
        root.addView(awDiag);

        awStrength = addSlider(root, "Reserved strength test (0..100%)", 100, 100);
        awAmplitude = addSlider(root, "Reserved music amplitude test (0..60)", 60, 60);

        Button effect30 = button("Effect 30 — AICY_WAKE");
        effect30.setOnClickListener(v -> call(service -> service.startAwEffect(
                30, selectedColor(), awStrength.getProgress(), awAmplitude.getProgress(), 0)));
        root.addView(effect30);

        Button effect32 = button("Effect 32 — native rainbow");
        effect32.setOnClickListener(v -> call(service -> service.startAwEffect(
                32, selectedColor(), awStrength.getProgress(), awAmplitude.getProgress(), 0)));
        root.addView(effect32);

        Button effect38 = button("Effect 38 — stock music-light preview (12 s)");
        effect38.setOnClickListener(v -> call(service -> service.startAwEffect(
                38, selectedColor(), awStrength.getProgress(), awAmplitude.getProgress(), 0)));
        root.addView(effect38);

        Button awStop = button("Stop stock effect / music preview");
        awStop.setOnClickListener(v -> call(IRingService::stopAwEffect));
        root.addView(awStop);

        TextView section = text("Direct AW20072 frame (root/Sui experiment)", 20, true);
        section.setPadding(0, dp(24), 0, dp(8));
        root.addView(section);

        TextView lowNote = text("ADB-shell cannot access /sys/class/leds on your stock Flyme, so these controls are expected to fail without root/Sui. Kept for future low-level testing.", 12, false);
        lowNote.setPadding(0, 0, 0, dp(8));
        root.addView(lowNote);

        Button sysfsDiag = button("AW20072 / 16-LED sysfs diagnostics");
        sysfsDiag.setOnClickListener(v -> call(IRingService::sysfsDiagnostics));
        root.addView(sysfsDiag);

        ledIndexLabel = text("LED index: 0", 14, true);
        ledIndexLabel.setPadding(0, dp(8), 0, 0);
        root.addView(ledIndexLabel);
        ledIndex = new SeekBar(this);
        ledIndex.setMax(15);
        ledIndex.setProgress(0);
        ledIndex.setOnSeekBarChangeListener(new SimpleSeekListener(() ->
                ledIndexLabel.setText("LED index: " + ledIndex.getProgress())));
        root.addView(ledIndex, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ledBrightness = addSlider(root, "Per-LED hardware brightness (0..60)", 60, 20);

        Button oneLed = button("Light ONLY selected LED (root/Sui)");
        oneLed.setOnClickListener(v -> call(service -> service.setSingleLed(
                ledIndex.getProgress(), selectedColor(), ledBrightness.getProgress())));
        root.addView(oneLed);

        Button allLeds = button("Light all 16 LEDs (root/Sui)");
        allLeds.setOnClickListener(v -> call(service -> service.setAllLeds(
                selectedColor(), ledBrightness.getProgress())));
        root.addView(allLeds);

        Button spin = button("3-color raw-frame spin (root/Sui)");
        spin.setOnClickListener(v -> call(service -> service.startThreeColorSpin(ledBrightness.getProgress())));
        root.addView(spin);

        Button clear = button("Clear raw 16-LED frame");
        clear.setOnClickListener(v -> call(IRingService::clearLedFrame));
        root.addView(clear);

        Button off = button("Turn ring off / stop everything");
        off.setOnClickListener(v -> call(IRingService::off));
        root.addView(off);

        status = text("", 12, false);
        status.setTextIsSelectable(true);
        status.setPadding(0, dp(18), 0, dp(40));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            appendStatus("Shizuku is not running. Start Shizuku/Sui first.");
            return;
        }
        if (Shizuku.isPreV11()) {
            appendStatus("Shizuku pre-v11 is unsupported.");
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            appendStatus("Shizuku access already granted.");
            bindUserService();
            return;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            appendStatus("Shizuku permission was denied with 'don't ask again'. Re-enable it in Shizuku.");
            return;
        }
        Shizuku.requestPermission(REQ_SHIZUKU);
    }

    private void bindUserService() {
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        } catch (Throwable t) {
            appendStatus("bindUserService ERROR: " + RingController.rootMessage(t));
        }
    }

    private void refreshShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            appendStatus("Shizuku: not running / binder unavailable.");
            return;
        }
        int uid = safeShizukuUid();
        String mode = uid == 0 ? "ROOT/Sui" : uid == 2000 ? "ADB shell" : "uid=" + uid;
        appendStatus("Shizuku: running (" + mode + ")");
        try {
            int perm = Shizuku.checkRemotePermission("android.permission.CONTROL_DEVICE_LIGHTS");
            appendStatus("Remote CONTROL_DEVICE_LIGHTS: " + (perm == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        } catch (Throwable t) {
            appendStatus("Remote permission check failed: " + RingController.rootMessage(t));
        }
    }

    private int safeShizukuUid() {
        try { return Shizuku.getUid(); } catch (Throwable ignored) { return -1; }
    }

    private void runDiagnostics() {
        refreshShizukuStatus();
        if (ringService == null) {
            appendStatus("Ring service is not connected yet.");
            return;
        }
        try {
            appendStatus("\n" + ringService.diagnostics());
        } catch (RemoteException e) {
            appendStatus("Diagnostics ERROR: " + e);
        }
    }

    private interface RemoteCall { String run(IRingService service) throws RemoteException; }

    private void call(RemoteCall action) {
        if (ringService == null) {
            appendStatus("Not connected. Grant Shizuku access first.");
            return;
        }
        try {
            appendStatus(action.run(ringService));
        } catch (RemoteException e) {
            appendStatus("Remote ERROR: " + e);
        }
    }

    private int selectedColor() {
        return Color.rgb(red.getProgress(), green.getProgress(), blue.getProgress());
    }

    private int scaledPreviewColor() {
        int br = brightness == null ? 255 : brightness.getProgress();
        int r = red == null ? 0 : (red.getProgress() * br + 127) / 255;
        int g = green == null ? 0 : (green.getProgress() * br + 127) / 255;
        int b = blue == null ? 0 : (blue.getProgress() * br + 127) / 255;
        return Color.rgb(r, g, b);
    }

    private void updatePreview() {
        if (preview != null && red != null) preview.setBackgroundColor(scaledPreviewColor());
    }

    private SeekBar addSlider(LinearLayout root, String label, int max, int initial) {
        TextView caption = text(label, 14, true);
        caption.setPadding(0, dp(4), 0, 0);
        root.addView(caption);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(initial);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.rgb(30, 30, 30));
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.START);
        return tv;
    }

    private void appendStatus(String line) {
        runOnUiThread(() -> {
            if (status == null) return;
            String old = status.getText().toString();
            status.setText(old.isEmpty() ? line : old + "\n" + line);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final Runnable callback;
        SimpleSeekListener(Runnable callback) { this.callback = callback; }
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { callback.run(); }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
