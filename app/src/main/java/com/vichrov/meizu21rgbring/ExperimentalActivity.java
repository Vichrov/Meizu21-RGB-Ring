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

/** v1.3: direct vendor.meizu.hardware.aw_light@1.0 HIDL tests. */
public final class ExperimentalActivity extends Activity {
    private static final int REQ = 7101;
    private IRingService service;
    private TextView log;

    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", RingUserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb_ring")
            .tag("meizu21_rgb_ring")
            .version(13)
            .debuggable(true);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IRingService.Stub.asInterface(binder);
            append("UserService connected.\nReady for direct HIDL tests.");
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
        setContentView(buildUi());
        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        connect();
    }

    @Override protected void onDestroy() {
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
        title.setText("Meizu 21 RGB Ring — v1.3 Direct HIDL");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25,25,25));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Bypass Android LightState and call stock vendor.meizu.hardware.aw_light@1.0::IAwLight directly from the Shizuku shell process. We can control command order: switchEffect, color_set_phase(R,G,B), music_phase(20 values), start/stop.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(6), 0, dp(18));
        root.addView(note);

        root.addView(button("Connect / grant Shizuku", v -> connect()));
        root.addView(button("0. Direct HIDL diagnostics", v -> call(IRingService::directHidlDiagnostics)));

        addHeader(root, "Baseline / direct color");
        root.addView(button("1. PURE 47 + direct RED", v -> call(s -> s.runDirectHidlTest(1, Color.RED))));
        root.addView(button("1B. PURE 47 + direct BLUE", v -> call(s -> s.runDirectHidlTest(1, Color.BLUE))));

        addHeader(root, "MUSIC 38 — direct command ordering");
        root.addView(button("2. RED: color + one-hot BEFORE start", v -> call(s -> s.runDirectHidlTest(2, Color.RED))));
        root.addView(button("2B. BLUE: color + one-hot BEFORE start", v -> call(s -> s.runDirectHidlTest(2, Color.BLUE))));
        root.addView(button("3. RED: start BEFORE color + one-hot", v -> call(s -> s.runDirectHidlTest(3, Color.RED))));
        root.addView(button("3B. BLUE: start BEFORE color + one-hot", v -> call(s -> s.runDirectHidlTest(3, Color.BLUE))));
        root.addView(button("4. RED direct sweep; force color every frame", v -> call(s -> s.runDirectHidlTest(4, Color.RED))));
        root.addView(button("4B. BLUE direct sweep; force color every frame", v -> call(s -> s.runDirectHidlTest(4, Color.BLUE))));

        addHeader(root, "Cross-test: PURE effect + music phase");
        root.addView(button("5. PURE 47 RED + one-hot phase 0", v -> call(s -> s.runDirectHidlTest(5, Color.RED))));
        root.addView(button("6. PURE 47 RED + moving music-phase", v -> call(s -> s.runDirectHidlTest(6, Color.RED))));
        root.addView(button("6B. PURE 47 BLUE + moving music-phase", v -> call(s -> s.runDirectHidlTest(6, Color.BLUE))));

        addHeader(root, "Raw state tests");
        root.addView(button("7. No switchEffect: RED + one-hot + start", v -> call(s -> s.runDirectHidlTest(7, Color.RED))));
        root.addView(button("8. MUSIC 38 RED + all 20 = 60", v -> call(s -> s.runDirectHidlTest(8, Color.RED))));
        root.addView(button("Stop direct HIDL", v -> call(IRingService::stopDirectHidl)));
        root.addView(button("Stop everything", v -> call(IRingService::off)));

        TextView guide = new TextView(this);
        guide.setText("First run 0. If it reports a CONNECT ERROR, send the full log. If diagnostics succeeds, test 1/1B first: RED and BLUE must change the whole ring if direct color_set_phase works. Then test 2/2B, 3/3B and 4/4B. For each, report whether the selected color is respected, whether a highlighted sector exists/moves, and whether the stock rainbow/music background remains.");
        guide.setTextSize(13);
        guide.setTextColor(Color.DKGRAY);
        guide.setPadding(0, dp(18), 0, dp(8));
        root.addView(guide);

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

    private void addHeader(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTextColor(Color.rgb(30,30,30));
        t.setPadding(0, dp(22), 0, dp(8));
        root.addView(t);
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
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind();
        else Shizuku.requestPermission(REQ);
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
        }, "ring-v13-call").start();
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
