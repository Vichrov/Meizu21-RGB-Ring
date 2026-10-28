package com.vichrov.meizu21rgbring;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

/** Compact experimental screen. v0.9 reproduces stock Settings RingtoneExt preview. */
public final class ExperimentalActivity extends Activity {
    private static final int REQ = 7101;
    private IRingService service;
    private TextView log;
    private AppRingtonePreviewController appMusicPreview;

    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", RingUserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb_ring")
            .tag("meizu21_rgb_ring")
            .version(9)
            .debuggable(true);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IRingService.Stub.asInterface(binder);
            append("UserService connected.\nReady.");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            append("UserService disconnected.");
        }
    };

    private final Shizuku.OnBinderReceivedListener binderListener = () -> {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind();
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, result) -> {
        if (requestCode == REQ && result == PackageManager.PERMISSION_GRANTED) bind();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appMusicPreview = new AppRingtonePreviewController(this);
        setContentView(buildUi());
        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        connect();
    }

    @Override protected void onDestroy() {
        if (appMusicPreview != null) appMusicPreview.stop();
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    private android.view.View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, dp(28), p, p);
        root.setBackgroundColor(Color.rgb(250,250,250));

        TextView title = new TextView(this);
        title.setText("Meizu 21 RGB Ring — v0.9");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(25,25,25));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Exact stock Settings music-preview path: RingtoneManager + Meizu's East ville memory URI + AudioAttributes tag light_preview_music. Shizuku is used only to read the stock Lights service.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(6), 0, dp(18));
        root.addView(note);

        root.addView(button("Connect / grant Shizuku", v -> connect()));
        root.addView(button("1. Exact STOCK MUSIC capture", v -> runAppProcessMusicCapture()));
        root.addView(button("2. Start exact stock MUSIC preview only", v -> append(appMusicPreview.start())));
        root.addView(button("3. Probe current stock LightState", v -> call(IRingService::stockDiagnostics)));
        root.addView(button("Show recovered stock preview details", v -> append(appMusicPreview.diagnostics())));
        root.addView(button("Stop stock MUSIC preview", v -> append(appMusicPreview.stop())));

        TextView arrayTitle = new TextView(this);
        arrayTitle.setText("brightnessArray (negative-control tests)");
        arrayTitle.setTextSize(18);
        arrayTitle.setTextColor(Color.rgb(30,30,30));
        arrayTitle.setPadding(0, dp(22), 0, dp(8));
        root.addView(arrayTitle);

        root.addView(button("Array: ONLY segment 0 = 60", v -> call(s -> s.testBrightnessArray(0, Color.RED))));
        root.addView(button("Array: alternating 60 / 0", v -> call(s -> s.testBrightnessArray(1, Color.RED))));
        root.addView(button("Array: ramp 0 → 60", v -> call(s -> s.testBrightnessArray(2, Color.RED))));
        root.addView(button("Array: all 16 = 60", v -> call(s -> s.testBrightnessArray(3, Color.RED))));
        root.addView(button("Stop experimental Lights session", v -> call(IRingService::stopBrightnessArrayTest)));
        root.addView(button("Stop everything", v -> {
            if (appMusicPreview != null) appMusicPreview.stop();
            call(IRingService::off);
        }));

        log = new TextView(this);
        log.setTextSize(12);
        log.setTextColor(Color.rgb(30,30,30));
        log.setTextIsSelectable(true);
        log.setPadding(0, dp(20), 0, dp(40));
        root.addView(log, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void runAppProcessMusicCapture() {
        if (service == null) {
            append("Not connected.");
            return;
        }
        new Thread(() -> {
            try {
                String start = appMusicPreview.start();
                append("APP UID / STOCK RINGTONE: " + start);
                if (start.contains("ERROR")) return;
                Thread.sleep(900);
                append("Capturing while exact stock Ringtone preview is playing...\n" + service.stockDiagnostics());
                Thread.sleep(250);
                append(appMusicPreview.stop());
            } catch (Throwable t) {
                append("CAPTURE ERROR: " + RingController.rootMessage(t));
                if (appMusicPreview != null) appMusicPreview.stop();
            }
        }, "ring-v09-stock-ringtone-capture").start();
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private void connect() {
        if (!Shizuku.pingBinder()) {
            append("Shizuku is not running.");
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bind();
        } else {
            Shizuku.requestPermission(REQ);
        }
    }

    private void bind() {
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable t) { append("Bind error: " + RingController.rootMessage(t)); }
    }

    private interface Call { String run(IRingService s) throws RemoteException; }

    private void call(Call c) {
        if (service == null) {
            append("Not connected.");
            return;
        }
        new Thread(() -> {
            try { append(c.run(service)); }
            catch (Throwable t) { append("ERROR: " + RingController.rootMessage(t)); }
        }, "ring-v09-call").start();
    }

    private void append(String text) {
        runOnUiThread(() -> {
            if (log == null) return;
            String old = log.getText().toString();
            log.setText(old.isEmpty() ? text : old + "\n\n" + text);
        });
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
