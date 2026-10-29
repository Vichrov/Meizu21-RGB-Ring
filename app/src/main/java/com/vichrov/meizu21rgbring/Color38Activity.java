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

/** v1.2: force RGB color into EFFECT_ID_MUSIC=38 while preserving its 20-phase mask. */
public final class Color38Activity extends Activity {
    private static final int REQ = 7102;
    private IColor38Service service;
    private TextView log;

    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", Color38UserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb38")
            .tag("meizu21_rgb38")
            .version(12)
            .debuggable(true);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IColor38Service.Stub.asInterface(binder);
            append("UserService connected.\nReady for effect 38 forced-color tests.");
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
        try { if (service != null) service.stop(); } catch (Throwable ignored) {}
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
        title.setText("Meizu 21 RGB Ring — v1.2");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(25,25,25));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("services.jar shows LightsService always forwards LightState.color to aw_color_set_phase(), even for MUSIC effect 38. v1.0 left that color at zero. This build forces RGB while keeping the same 20-phase one-hot mask that produced the moving highlight.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(6), 0, dp(18));
        root.addView(note);

        root.addView(button("Connect / grant Shizuku", v -> connect()));

        TextView fixed = section("Fixed phase — does effect 38 accept RGB?");
        root.addView(fixed);
        root.addView(button("1. RED — one-hot phase 0", v -> call(s -> s.runTest(1, Color.RED))));
        root.addView(button("2. BLUE — one-hot phase 0", v -> call(s -> s.runTest(1, Color.BLUE))));
        root.addView(button("3. GREEN — one-hot phase 0", v -> call(s -> s.runTest(1, Color.GREEN))));
        root.addView(button("4. RED — one-hot phase 5", v -> call(s -> s.runTest(2, Color.RED))));

        TextView moving = section("Moving one-hot — same geometry, forced color");
        root.addView(moving);
        root.addView(button("5. RED moving one-hot (2.4 s/cycle)", v -> call(s -> s.runTest(3, Color.RED))));
        root.addView(button("6. BLUE moving one-hot (2.4 s/cycle)", v -> call(s -> s.runTest(3, Color.BLUE))));
        root.addView(button("7. GREEN moving one-hot (2.4 s/cycle)", v -> call(s -> s.runTest(3, Color.GREEN))));

        TextView dynamic = section("Color update while phase is fixed");
        root.addView(dynamic);
        root.addView(button("8. Fixed phase 0: RED ↔ BLUE every 0.7 s", v -> call(s -> s.runTest(6, Color.RED))));

        TextView masks = section("Mask controls");
        root.addView(masks);
        root.addView(button("9. Effect 38 RED — all 20 = 60", v -> call(s -> s.runTest(4, Color.RED))));
        root.addView(button("10. Effect 38 RED — alternating 60/0", v -> call(s -> s.runTest(5, Color.RED))));
        root.addView(button("11. STOCK-LIKE 38 moving one-hot — NO setColor", v -> call(s -> s.runTest(7, Color.RED))));

        root.addView(button("Stop test", v -> call(IColor38Service::stop)));

        TextView guide = new TextView(this);
        guide.setText("What matters most: compare 1/2/3 — does the palette visibly become red/blue/green? Compare 5/6/7 — does the same moving highlight keep its path but change color? Test 8 is decisive: if the visible colors flip RED↔BLUE while the highlighted position stays fixed, effect 38 accepts live color updates. If every forced-color test looks identical to 11, effect 38 ignores color and the next step is direct aw_light HAL access.");
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

    private TextView section(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setTextColor(Color.rgb(30,30,30));
        v.setPadding(0, dp(20), 0, dp(8));
        return v;
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

    private interface Call { String run(IColor38Service s) throws RemoteException; }

    private void call(Call c) {
        if (service == null) {
            append("Not connected.");
            return;
        }
        new Thread(() -> {
            try { append(c.run(service)); }
            catch (Throwable t) { append("ERROR: " + RingController.rootMessage(t)); }
        }, "ring-v12-call").start();
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
