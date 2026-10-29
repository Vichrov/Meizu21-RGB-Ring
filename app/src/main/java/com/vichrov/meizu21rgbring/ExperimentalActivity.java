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

/** v1.1: effect 50 IN_CALL_MUSIC spatial/color tests recovered from stock services.jar. */
public final class ExperimentalActivity extends Activity {
    private static final int REQ = 7101;
    private IRingService service;
    private TextView log;

    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", RingUserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb_ring")
            .tag("meizu21_rgb_ring")
            .version(11)
            .debuggable(true);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IRingService.Stub.asInterface(binder);
            append("UserService connected.\nReady for effect 50 tests.");
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
        title.setText("Meizu 21 RGB Ring — v1.1");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(25,25,25));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("New target: EFFECT_ID_IN_CALL_MUSIC = 50. Stock services.jar uses effect 50 with setColor(color) + exactly 20 brightness/phase values. Test whether it combines the spatial behavior of effect 38 with the selectable RGB behavior of effect 64.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(6), 0, dp(18));
        root.addView(note);

        root.addView(button("Connect / grant Shizuku", v -> connect()));

        TextView effect50 = new TextView(this);
        effect50.setText("Effect 50 — fixed positions (RED)");
        effect50.setTextSize(19);
        effect50.setTextColor(Color.rgb(30,30,30));
        effect50.setPadding(0, dp(12), 0, dp(8));
        root.addView(effect50);

        root.addView(button("1. RED one-hot — phase 0", v -> call(s -> s.testBrightnessArray(40, Color.RED))));
        root.addView(button("2. RED one-hot — phase 5", v -> call(s -> s.testBrightnessArray(41, Color.RED))));
        root.addView(button("3. RED one-hot — phase 10", v -> call(s -> s.testBrightnessArray(42, Color.RED))));
        root.addView(button("4. RED one-hot — phase 15", v -> call(s -> s.testBrightnessArray(43, Color.RED))));
        root.addView(button("5. RED one-hot — phase 19", v -> call(s -> s.testBrightnessArray(44, Color.RED))));

        TextView moving = new TextView(this);
        moving.setText("Effect 50 — movement / color");
        moving.setTextSize(19);
        moving.setTextColor(Color.rgb(30,30,30));
        moving.setPadding(0, dp(22), 0, dp(8));
        root.addView(moving);

        root.addView(button("6. RED moving one-hot (20 phases / 2.4 s)", v -> call(s -> s.testBrightnessArray(50, Color.RED))));
        root.addView(button("7. BLUE moving one-hot (20 phases / 2.4 s)", v -> call(s -> s.testBrightnessArray(50, Color.BLUE))));
        root.addView(button("8. GREEN alternating 60/0", v -> call(s -> s.testBrightnessArray(51, Color.GREEN))));
        root.addView(button("9. RED all 20 = 60", v -> call(s -> s.testBrightnessArray(52, Color.RED))));

        TextView control = new TextView(this);
        control.setText("Control tests from v1.0");
        control.setTextSize(18);
        control.setTextColor(Color.rgb(30,30,30));
        control.setPadding(0, dp(22), 0, dp(8));
        root.addView(control);

        root.addView(button("10. MUSIC 38 — moving one-hot reference", v -> call(s -> s.testBrightnessArray(20, Color.RED))));
        root.addView(button("11. Effect 64 RED — moving one-hot reference", v -> call(s -> s.testBrightnessArray(30, Color.RED))));
        root.addView(button("Stop phase session", v -> call(IRingService::stopBrightnessArrayTest)));
        root.addView(button("Stop everything", v -> call(IRingService::off)));

        TextView guide = new TextView(this);
        guide.setText("What to report: for 1–5, whether the lit/highlighted area changes position between phase numbers; for 6–7, whether one area travels around the ring and whether RED vs BLUE is respected; for 8, whether alternate phases create a spatial pattern or only global brightness; for 9, whether the whole ring becomes steady red or another built-in animation appears.");
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
        }, "ring-v11-call").start();
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
