package com.vichrov.meizu21rgbring;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** v2.0: polished no-root controller using only Flyme's system Lights API through Shizuku. */
public final class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 1001;
    private static final int PREVIEW_PRIORITY = 10009;

    private int BG, PANEL, INK, MUTED, ACCENT, ACCENT_TEXT, SOFT, CONTROL, DIVIDER, GOOD, WARN, DANGER;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private IRingService ringService;
    private LinearLayout root, page;
    private TextView connectionStatus, statusText, advancedOutput, colorValue;
    private RingPreviewView preview;
    private SeekBar red, green, blue, brightness;
    private SeekBar advancedEffect, advancedBrightness, advancedPercent, advancedAmplitude, advancedPlayer, advancedPriority;
    private EditText semaphoreInput;
    private int selectedPage;
    private int selectedColor = 0xFFFF5757;
    private int selectedBrightness = 255;
    private int selectedEffect = 47;
    private String lastMessage = "Готово к работе";

    private final Shizuku.UserServiceArgs userServiceArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.vichrov.meizu21rgbring", RingUserService.class.getName()))
            .daemon(false)
            .processNameSuffix("rgb_ring")
            .tag("meizu21_rgb_ring")
            .version(20)
            .debuggable(true);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            ringService = IRingService.Stub.asInterface(service);
            showMessage("Shizuku подключён · управление кольцом готово", false);
            refreshConnectionView();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            ringService = null;
            showMessage("Shizuku UserService отключён", false);
            refreshConnectionView();
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        refreshConnectionView();
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
    };
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        ringService = null;
        refreshConnectionView();
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, result) -> {
        if (requestCode != REQ_SHIZUKU) return;
        if (result == PackageManager.PERMISSION_GRANTED) bindUserService();
        else showMessage("Доступ Shizuku не разрешён", true);
        refreshConnectionView();
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPalette();
        getWindow().setDecorFitsSystemWindows(false);
        buildShell();
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
        refreshConnectionView();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadPalette();
        buildShell();
        refreshConnectionView();
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    private void loadPalette() {
        BG = getColor(R.color.page); PANEL = getColor(R.color.surface); INK = getColor(R.color.ink);
        MUTED = getColor(R.color.muted); ACCENT = getColor(R.color.accent); ACCENT_TEXT = getColor(R.color.accent_text);
        SOFT = getColor(R.color.soft); CONTROL = getColor(R.color.control); DIVIDER = getColor(R.color.divider);
        GOOD = getColor(R.color.good); WARN = getColor(R.color.warn); DANGER = getColor(R.color.danger);
        getWindow().getDecorView();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            c.setSystemBarsAppearance(light ? flags : 0, flags);
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20) + i.left, dp(12) + i.top, dp(20) + i.right, dp(12) + i.bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyWindowInsets();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Meizu Ring", 31, INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("NO ROOT", 11, ACCENT_TEXT);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(rounded(SOFT, 18));
        header.addView(badge);
        root.addView(header);
        TextView subtitle = text("Полное доступное управление RGB-кольцом через Flyme Lights API", 13, MUTED);
        subtitle.setPadding(0, dp(2), 0, dp(14));
        root.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(CONTROL, 24));
        String[] labels = {"Главная", "Эффекты", "Ещё"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button b = tabButton(labels[i], selectedPage == i, () -> {
                selectedPage = index;
                buildShell();
                refreshConnectionView();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) p.setMarginStart(dp(4));
            tabs.addView(b, p);
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(8), 0, dp(32));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        connectionStatus = null; statusText = null; advancedOutput = null; preview = null;
        red = green = blue = brightness = null;
        if (selectedPage == 0) showHome();
        else if (selectedPage == 1) showEffects();
        else showAdvanced();
    }

    private void showHome() {
        section(page, "Подключение");
        LinearLayout connect = card(page);
        connectionStatus = text(connectionDescription(), 15, INK);
        connectionStatus.setLineSpacing(dp(2), 1f);
        connect.addView(connectionStatus);
        TextView hint = text("Shizuku нужен только для системного permission CONTROL_DEVICE_LIGHTS. Root не используется.", 12, MUTED);
        connect.addView(hint);
        actionRow(connect,
                primary("Подключить", this::requestShizuku),
                button("Проверить", this::runCurrentState));

        section(page, "Кольцо");
        LinearLayout visual = card(page);
        visual.setGravity(Gravity.CENTER_HORIZONTAL);
        preview = new RingPreviewView(this);
        preview.setTrackColor(withAlpha(MUTED, 42));
        preview.setRingColor(selectedColor, selectedBrightness);
        visual.addView(preview, new LinearLayout.LayoutParams(-1, dp(220)));
        TextView visualCaption = text("16 физических RGB-сегментов · штатный API управляет эффектами целиком", 12, MUTED);
        visualCaption.setGravity(Gravity.CENTER);
        visual.addView(visualCaption);

        section(page, "Цвет");
        LinearLayout colorCard = card(page);
        colorValue = text(colorHex(), 17, INK);
        colorValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        colorCard.addView(colorValue);
        addSwatches(colorCard);
        red = slider(colorCard, "Красный", 255, Color.red(selectedColor), value -> updateSelectedColor());
        green = slider(colorCard, "Зелёный", 255, Color.green(selectedColor), value -> updateSelectedColor());
        blue = slider(colorCard, "Синий", 255, Color.blue(selectedColor), value -> updateSelectedColor());
        brightness = slider(colorCard, "Яркость PURE", 255, selectedBrightness, value -> {
            selectedBrightness = value;
            updatePreviewColor();
        });
        actionRow(colorCard,
                primary("Применить", () -> call(s -> s.setPureColor(selectedColor, selectedBrightness), () -> previewMode(RingPreviewView.MODE_STATIC))),
                button("Выключить", () -> call(IRingService::off, () -> {
                    if (preview != null) preview.setRingColor(Color.BLACK, 0);
                    previewMode(RingPreviewView.MODE_STATIC);
                })));

        section(page, "Быстрые режимы");
        LinearLayout quick = card(page);
        actionRow(quick,
                button("Радуга", () -> call(s -> s.setRainbow(selectedBrightness), () -> previewMode(RingPreviewView.MODE_RAINBOW))),
                button("Плавная RGB", () -> call(s -> s.startSoftwareRainbow(selectedBrightness), () -> previewMode(RingPreviewView.MODE_RAINBOW))));
        actionRow(quick,
                button("Музыка", () -> call(s -> s.applyAdvancedState(38, selectedColor, 0, 0, 0, 0, 60, PREVIEW_PRIORITY), () -> previewMode(RingPreviewView.MODE_MUSIC))),
                button("Фейерверк", () -> runEffect(56)));
        actionRow(quick,
                button("Уведомление", () -> runEffect(66)),
                button("Входящий вызов", () -> runEffect(65)));

        section(page, "Состояние");
        LinearLayout state = card(page);
        statusText = text(lastMessage, 13, MUTED);
        statusText.setTextIsSelectable(true);
        state.addView(statusText);
    }

    private void showEffects() {
        section(page, "Цвет для эффектов");
        LinearLayout colorCard = card(page);
        colorValue = text(colorHex(), 17, INK);
        colorValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        colorCard.addView(colorValue);
        addSwatches(colorCard);
        colorCard.addView(text("Не каждый штатный эффект использует переданный RGB: у музыки, радуги и части системных анимаций палитра задаётся самой Flyme.", 12, MUTED));

        effectGroup("Основные",
                e(47, "Чистый цвет"), e(32, "Радуга"), e(38, "Музыка"), e(42, "Уведомление"),
                e(41, "Входящий вызов"), e(49, "Громкость"));
        effectGroup("Aicy и погода",
                e(1, "Aicy · голос"), e(2, "Aicy · робот"), e(30, "Aicy · пробуждение"), e(51, "Солнечно"),
                e(52, "Облачно"), e(53, "Пасмурно"), e(54, "Снег"), e(55, "Дождь"), e(62, "Семафор"));
        effectGroup("Зарядка",
                e(34, "Зарядка"), e(35, "Заряд вверх"), e(36, "Заряд завершён"), e(37, "Анимация зарядки"),
                e(48, "Заряд вниз"), e(70, "Обратная беспроводная"), e(71, "FAW · включение"),
                e(72, "FAW · зарядка"), e(73, "FAW · заряд завершён"));
        effectGroup("Камера и система",
                e(39, "Камера · 10 с"), e(40, "Камера · 3 с"), e(43, "Красный конверт"), e(63, "Камера · готово"),
                e(64, "Музыка вызова · preview"), e(65, "Вызов · preview"), e(66, "Уведомление · preview"), e(74, "FAW · звонок"));
        effectGroup("Игры",
                e(44, "Вход в игру"), e(45, "Arena of Valor · kill"), e(46, "Game for Peace · старт"));
        effectGroup("Фейерверки",
                e(56, "Фейерверк 1"), e(57, "Фейерверк 2"), e(58, "Фейерверк 3"),
                e(59, "Фейерверк 4"), e(60, "Фейерверк 5"), e(61, "Фейерверк 6"));
        effectGroup("Музыкальные",
                e(38, "Музыка"), e(50, "Музыка звонка"), e(64, "Музыка звонка · preview"));

        section(page, "Управление");
        LinearLayout stop = card(page);
        stop.addView(text("Выбранный эффект: " + selectedEffect, 14, MUTED));
        actionRow(stop,
                primary("Выключить кольцо", () -> call(IRingService::off, null)),
                button("Текущее состояние", this::runCurrentState));
        statusText = text(lastMessage, 12, MUTED);
        statusText.setTextIsSelectable(true);
        stop.addView(statusText);
    }

    private void showAdvanced() {
        section(page, "Расширенный LightState");
        LinearLayout state = card(page);
        state.addView(text("Эти параметры реально доступны без root через system_server. Их визуальный смысл зависит от выбранного effect ID.", 13, MUTED));
        advancedEffect = slider(state, "Effect ID", 255, selectedEffect, value -> selectedEffect = value);
        advancedBrightness = slider(state, "Brightness", 255, 0, value -> {});
        advancedPercent = slider(state, "Percent / progress", 100, 0, value -> {});
        advancedAmplitude = slider(state, "Music amplitude", 60, 0, value -> {});
        advancedPlayer = slider(state, "Player ID", 16, 0, value -> {});
        advancedPriority = slider(state, "Priority: 10000 +", 11, 9, value -> {});
        TextView semTitle = text("Semaphore letter", 13, MUTED);
        semTitle.setPadding(0, dp(8), 0, dp(4));
        state.addView(semTitle);
        semaphoreInput = new EditText(this);
        semaphoreInput.setSingleLine(true);
        semaphoreInput.setHint("символ, например A");
        semaphoreInput.setTextColor(INK);
        semaphoreInput.setHintTextColor(MUTED);
        semaphoreInput.setTextSize(15);
        semaphoreInput.setInputType(InputType.TYPE_CLASS_TEXT);
        semaphoreInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        semaphoreInput.setBackground(rounded(CONTROL, 18));
        state.addView(semaphoreInput, new LinearLayout.LayoutParams(-1, dp(50)));
        actionRow(state,
                primary("Отправить", this::applyAdvanced),
                button("Считать", this::runCurrentState));

        section(page, "Диагностика");
        LinearLayout diag = card(page);
        actionRow(diag,
                button("Lights API", () -> call(IRingService::diagnostics, null)),
                button("Flyme constants", () -> call(IRingService::stockDiagnostics, null)));
        advancedOutput = text(lastMessage, 12, MUTED);
        advancedOutput.setTextIsSelectable(true);
        advancedOutput.setLineSpacing(dp(2), 1f);
        diag.addView(advancedOutput);

        section(page, "Что доступно без root");
        LinearLayout info = card(page);
        info.addView(capability("Чистый RGB-цвет", "Да · effect 47, программная регулировка яркости", GOOD));
        info.addView(capability("Штатная радуга", "Да · effect 32", GOOD));
        info.addView(capability("Полный каталог эффектов Flyme", "Да · через effect ID", GOOD));
        info.addView(capability("Percent / amplitude / playerId / semaphore", "Да · через LightState, поддержка зависит от эффекта", WARN));
        info.addView(capability("16 сегментов по отдельности", "Нет · raw AW20072 закрыт SELinux для shell/Shizuku", DANGER));
        info.addView(capability("Root", "Не требуется для всех функций этого приложения", GOOD));

        section(page, "О приложении");
        LinearLayout about = card(page);
        about.addView(text("Meizu Ring v2.0 · No-Root Flyme Edition", 17, INK));
        about.addView(text("Интерфейс основан на том же Flyme 12 design language, что и Local Eyes: большие заголовки, спокойные карточки, системная светлая/тёмная тема и синий акцент.", 12, MUTED));
        about.addView(text("Аппаратно кольцо содержит 16 RGB-сегментов, но Flyme не публикует raw-frame API наружу. Поэтому v2.0 использует максимально полный безопасный системный путь LightsManager.", 12, MUTED));
    }

    private void effectGroup(String title, EffectSpec... effects) {
        section(page, title);
        LinearLayout box = card(page);
        for (int i = 0; i < effects.length; i += 2) {
            Button a = effectButton(effects[i]);
            if (i + 1 < effects.length) actionRow(box, a, effectButton(effects[i + 1]));
            else {
                LinearLayout row = new LinearLayout(this);
                row.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.setMargins(0, dp(6), 0, 0); box.addView(row, rp);
            }
        }
    }

    private Button effectButton(EffectSpec spec) {
        return button(spec.id + " · " + spec.name, () -> runEffect(spec.id));
    }

    private void runEffect(int id) {
        selectedEffect = id;
        if (id == 47) {
            call(s -> s.setPureColor(selectedColor, selectedBrightness), () -> previewMode(RingPreviewView.MODE_STATIC));
        } else if (id == 32) {
            call(s -> s.setRainbow(selectedBrightness), () -> previewMode(RingPreviewView.MODE_RAINBOW));
        } else if (id == 38 || id == 50 || id == 64) {
            call(s -> s.applyAdvancedState(id, selectedColor, 0, 0, 0, 0, 60, PREVIEW_PRIORITY), () -> previewMode(RingPreviewView.MODE_MUSIC));
        } else {
            call(s -> s.startStockEffect(id, selectedColor, PREVIEW_PRIORITY), null);
        }
    }

    private void applyAdvanced() {
        int semaphore = 0;
        String s = semaphoreInput == null ? "" : semaphoreInput.getText().toString();
        if (!s.isEmpty()) semaphore = s.charAt(0);
        int effect = advancedEffect.getProgress();
        selectedEffect = effect;
        int finalSemaphore = semaphore;
        call(service -> service.applyAdvancedState(
                effect, selectedColor,
                advancedBrightness.getProgress(), advancedPercent.getProgress(), advancedPlayer.getProgress(),
                finalSemaphore, advancedAmplitude.getProgress(), 10000 + advancedPriority.getProgress()), null);
    }

    private void runCurrentState() { call(IRingService::currentState, null); }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            showMessage("Shizuku не запущен. Запусти его через ADB/Wireless debugging.", true);
            return;
        }
        if (Shizuku.isPreV11()) {
            showMessage("Нужна Shizuku 11 или новее", true);
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindUserService();
            return;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            showMessage("Разрешение отключено. Включи приложение в Shizuku вручную.", true);
            return;
        }
        Shizuku.requestPermission(REQ_SHIZUKU);
    }

    private void bindUserService() {
        try { Shizuku.bindUserService(userServiceArgs, serviceConnection); }
        catch (Throwable t) { showMessage("Ошибка подключения: " + RingController.rootMessage(t), true); }
    }

    private String connectionDescription() {
        if (!Shizuku.pingBinder()) return "Shizuku не запущен\nУправление кольцом пока недоступно";
        int uid = safeShizukuUid();
        String mode = uid == 2000 ? "ADB shell" : uid == 0 ? "root" : "uid=" + uid;
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
            return "Shizuku запущен · " + mode + "\nНужно разрешить доступ приложению";
        String remote;
        try {
            remote = Shizuku.checkRemotePermission("android.permission.CONTROL_DEVICE_LIGHTS") == PackageManager.PERMISSION_GRANTED
                    ? "CONTROL_DEVICE_LIGHTS разрешён" : "CONTROL_DEVICE_LIGHTS не разрешён";
        } catch (Throwable t) { remote = "permission check недоступен"; }
        return (ringService != null ? "Подключено" : "Подключение…") + " · " + mode + "\n" + remote;
    }

    private void refreshConnectionView() {
        runOnUiThread(() -> { if (connectionStatus != null) connectionStatus.setText(connectionDescription()); });
    }

    private int safeShizukuUid() { try { return Shizuku.getUid(); } catch (Throwable ignored) { return -1; } }

    private interface RemoteCall { String run(IRingService service) throws RemoteException; }

    private void call(RemoteCall action, Runnable visualUpdate) {
        if (ringService == null) {
            showMessage("Сначала подключи Shizuku", true);
            return;
        }
        worker.execute(() -> {
            String result;
            try { result = action.run(ringService); }
            catch (Throwable t) { result = "Ошибка: " + RingController.rootMessage(t); }
            final String message = result;
            runOnUiThread(() -> {
                if (visualUpdate != null && !message.toLowerCase().contains("error")) visualUpdate.run();
                showMessage(message, message.toLowerCase().contains("error"));
            });
        });
    }

    private void showMessage(String message, boolean toast) {
        lastMessage = message;
        if (statusText != null) statusText.setText(message);
        if (advancedOutput != null) advancedOutput.setText(message);
        if (toast) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateSelectedColor() {
        if (red == null || green == null || blue == null) return;
        selectedColor = Color.rgb(red.getProgress(), green.getProgress(), blue.getProgress());
        if (colorValue != null) colorValue.setText(colorHex());
        updatePreviewColor();
    }

    private void updatePreviewColor() {
        if (preview != null) preview.setRingColor(selectedColor, selectedBrightness);
    }

    private void setSelectedColor(int color) {
        selectedColor = color;
        if (red != null) red.setProgress(Color.red(color));
        if (green != null) green.setProgress(Color.green(color));
        if (blue != null) blue.setProgress(Color.blue(color));
        if (colorValue != null) colorValue.setText(colorHex());
        updatePreviewColor();
    }

    private String colorHex() { return String.format("#%06X", selectedColor & 0xFFFFFF); }

    private void previewMode(int mode) { if (preview != null) preview.setMode(mode); }

    private void addSwatches(LinearLayout parent) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, dp(10));
        int[] colors = {0xFFFF5757, 0xFFFF9F43, 0xFFFFD93D, 0xFF35C76F, 0xFF31CDBE, 0xFF4B8FFF, 0xFF9B7BFF, 0xFFFFFFFF};
        for (int c : colors) {
            View swatch = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL); bg.setColor(c); bg.setStroke(dp(1), withAlpha(MUTED, 60));
            swatch.setBackground(bg); swatch.setContentDescription("Цвет " + String.format("#%06X", c & 0xFFFFFF));
            swatch.setOnClickListener(v -> setSelectedColor(c));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(42)); p.setMarginEnd(dp(10)); row.addView(swatch, p);
        }
        scroll.addView(row);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private SeekBar slider(LinearLayout parent, String label, int max, int initial, IntChanged listener) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 13, MUTED); line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(String.valueOf(initial), 13, INK); value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); line.addView(value);
        line.setPadding(0, dp(6), 0, 0); parent.addView(line);
        SeekBar bar = new SeekBar(this); bar.setMax(max); bar.setProgress(initial);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT)); bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { value.setText(String.valueOf(progress)); listener.changed(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return bar;
    }

    private TextView capability(String title, String body, int color) {
        TextView t = text(title + "\n" + body, 14, INK);
        t.setLineSpacing(dp(2), 1f); t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable d = rounded(withAlpha(color, isNight() ? 32 : 22), 16); d.setStroke(dp(1), withAlpha(color, 70)); t.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(8)); t.setLayoutParams(p);
        return t;
    }

    private TextView section(LinearLayout parent, String label) {
        TextView t = text(label, 13, MUTED); t.setPadding(dp(16), dp(22), dp(16), dp(9)); parent.addView(t); return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(16), dp(16), dp(16)); box.setBackground(rounded(PANEL, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(10)); parent.addView(box, p); return box;
    }

    private void actionRow(LinearLayout parent, Button... buttons) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (i > 0) p.setMarginStart(dp(8)); row.addView(buttons[i], p);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(10), 0, 0); parent.addView(row, p);
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(enabledColors(INK)); b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(48));
        b.setPadding(dp(10), dp(9), dp(10), dp(9)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(ACCENT, 32)), rounded(CONTROL, 22), null));
        b.setOnClickListener(v -> action.run()); return b;
    }

    private Button primary(String label, Runnable action) {
        Button b = button(label, action); b.setTextColor(enabledColors(Color.WHITE));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, rounded(CONTROL, 22));
        states.addState(new int[]{}, rounded(0xFF206CFF, 22));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), states, null)); return b;
    }

    private Button tabButton(String label, boolean selected, Runnable action) {
        Button b = button(label, action); b.setMinHeight(0); b.setTextSize(13);
        b.setTextColor(selected ? ACCENT_TEXT : MUTED);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(ACCENT, 30)), rounded(selected ? PANEL : Color.TRANSPARENT, 20), null));
        return b;
    }

    private TextView text(String value, int sp, int color) {
        TextView tv = new TextView(this); tv.setText(value); tv.setTextSize(sp); tv.setTextColor(color); tv.setPadding(0, dp(5), 0, dp(5)); return tv;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private ColorStateList enabledColors(int color) {
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{MUTED, color});
    }

    private int withAlpha(int color, int alpha) { return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)); }
    private boolean isNight() { return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density * v + .5f); }

    private static EffectSpec e(int id, String name) { return new EffectSpec(id, name); }
    private static final class EffectSpec { final int id; final String name; EffectSpec(int id, String name) { this.id = id; this.name = name; } }
    private interface IntChanged { void changed(int value); }
}
