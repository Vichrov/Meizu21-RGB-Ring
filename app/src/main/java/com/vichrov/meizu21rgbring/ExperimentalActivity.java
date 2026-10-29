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

/** v1.0: tests reconstructed directly from stock services.jar AudioVisualizerHander/LightsService. */
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
            .version(10)
            .debuggable(true);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IRingService.Stub.asInterface(binder);
            append("UserService connected.\nReady for stock 20-phase tests.");
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
        title.setText("Meizu 21 RGB Ring — v1.0");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(25,25,25));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Recovered from stock services.jar: AudioVisualizerHander sends exactly 20 Integer values to LightState.setBrightnessArray(). Effect 38 is MUSIC; effect 64 is in-call music preview and also accepts a global color. No setBrightness() or setPercent() is used for these frames.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(6), 0, dp(18));
        root.addView(note);

        root.addView(button("Connect / grant Shizuku", v -> connect()));

        TextView directTitle = new TextView(this);
        directTitle.setText("Exact stock 20-phase path");
        directTitle.setTextSize(19);
        directTitle.setTextColor(Color.rgb(30,30,30));
        directTitle.setPadding(0, dp(12), 0, dp(8));
        root.addView(directTitle);

        root.addView(button("1. MUSIC 38 — one hot [60,0…] (20 values)", v -> call(s -> s.testBrightnessArray(0, Color.RED))));
        root.addView(button("2. MUSIC 38 — moving one-hot sweep", v -> call(s -> s.testBrightnessArray(20, Color.RED))));
        root.addView(button("3. MUSIC 38 — alternating 60/0", v -> call(s -> s.testBrightnessArray(1, Color.RED))));
        root.addView(button("4. MUSIC 38 — all 20 = 60", v -> call(s -> s.testBrightnessArray(3, Color.RED))));

        TextView colorTitle = new TextView(this);
        colorTitle.setText("Effect 64: 20-phase + selectable global color");
        colorTitle.setTextSize(18);
        colorTitle.setTextColor(Color.rgb(30,30,30));
        colorTitle.setPadding(0, dp(22), 0, dp(8));
        root.addView(colorTitle);

        root.addView(button("5. Effect 64 RED — one hot", v -> call(s -> s.testBrightnessArray(10, Color.RED))));
        root.addView(button("6. Effect 64 RED — moving one-hot", v -> call(s -> s.testBrightnessArray(30, Color.RED))));
        root.addView(button("7. Effect 64 BLUE — moving one-hot", v -> call(s -> s.testBrightnessArray(30, Color.BLUE))));
        root.addView(button("8. Effect 64 GREEN — alternating", v -> call(s -> s.testBrightnessArray(11, Color.GREEN))));

        root.addView(button("Stop 20-phase session", v -> call(IRingService::stopBrightnessArrayTest)));

        TextView previewTitle = new TextView(this);
        previewTitle.setText("Stock preview reference");
        previewTitle.setTextSize(18);
        previewTitle.setTextColor(Color.rgb(30,30,30));
        previewTitle.setPadding(0, dp(22), 0, dp(8));
        root.addView(previewTitle);

        TextView previewNote = new TextView(this);
        previewNote.setText("services.jar revealed that light_preview_music is hard-gated to package com.android.settings. Our app can reproduce the Ringtone, but Flyme will not treat our package as the Settings preview. These buttons are kept only as reference.");
        previewNote.setTextSize(13);
        previewNote.setTextColor(Color.DKGRAY);
        previewNote.setPadding(0, 0, 0, dp(8));
        root.addView(previewNote);

        root.addView(button("Play recovered stock ringtone", v -> append(appMusicPreview.start())));
        root.addView(button("Probe current stock LightState", v -> call(IRingService::stockDiagnostics)));
        root.addView(button("Stop ringtone", v -> append(appMusicPreview.stop())));

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
        }, "ring-v10-call").start();
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
