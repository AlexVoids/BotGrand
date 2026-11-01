package ru.backgroundtools.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(245, 246, 250);
    private static final int INK = Color.rgb(20, 29, 49);
    private static final int MUTED = Color.rgb(101, 113, 136);
    private static final int BLUE = Color.rgb(83, 79, 210);
    private static final int WHITE = Color.WHITE;
    private static final int LINE = Color.rgb(229, 232, 240);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppEntry> allApps = new ArrayList<>();
    private LinearLayout root;
    private ListView listView;
    private TextView countView;
    private AppAdapter appAdapter;
    private boolean showSystem = false;
    private String search = "";
    private String selectedPackage;
    private boolean loaded = false;
    private TextView batteryStatus;

    private static final class AppEntry {
        final String name;
        final String pkg;
        final boolean system;
        final boolean launcher;

        AppEntry(String name, String pkg, boolean system, boolean launcher) {
            this.name = name;
            this.pkg = pkg;
            this.system = system;
            this.launcher = launcher;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            selectedPackage = state.getString("selectedPackage");
            search = state.getString("search", "");
            showSystem = state.getBoolean("showSystem", false);
        }
        getWindow().setStatusBarColor(Color.rgb(18, 26, 49));
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        // Android 15/16 draw behind system bars. Keep buttons out of the status/nav areas.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        if (selectedPackage == null) showHome();
        else showDetails();
        loadApps();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("selectedPackage", selectedPackage);
        out.putString("search", search);
        out.putBoolean("showSystem", showSystem);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedPackage != null && batteryStatus != null) {
            batteryStatus.setText(batteryText(selectedPackage));
        }
    }

    @Override
    public void onBackPressed() {
        if (selectedPackage != null) {
            selectedPackage = null;
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<AppEntry> found = new ArrayList<>();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                try {
                    String name = String.valueOf(pm.getApplicationLabel(info));
                    boolean system = (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    boolean launcher = pm.getLaunchIntentForPackage(info.packageName) != null;
                    found.add(new AppEntry(name, info.packageName, system, launcher));
                } catch (Exception ignored) {
                    // A package may disappear during scanning.
                }
            }
            Collator collator = Collator.getInstance(new Locale("ru"));
            Collections.sort(found, (a, b) -> {
                int c = collator.compare(a.name, b.name);
                return c != 0 ? c : a.pkg.compareToIgnoreCase(b.pkg);
            });
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(found);
                loaded = true;
                if (selectedPackage == null) refreshList();
                else showDetails();
            });
        });
    }

    private void showHome() {
        batteryStatus = null;
        root.removeAllViews();
        LinearLayout hero = col(20, 22, 20, 20);
        hero.setBackgroundColor(Color.rgb(18, 26, 49));
        TextView mark = tv("⚡  ИНСТРУМЕНТЫ ANDROID", 12, Color.rgb(184, 187, 255), true);
        hero.addView(mark);
        TextView title = tv("Фон без ограничений", 27, WHITE, true);
        addWithTop(hero, title, 8);
        TextView desc = tv("Все важные настройки фоновой работы в одном месте. Разрешения подтверждаются только в Android.", 14, Color.rgb(210, 217, 235), false);
        addWithTop(hero, desc, 7);
        TextView quick = tv("Открыть список исключений батареи  ›", 15, WHITE, true);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(dp(14), dp(15), dp(10), dp(15));
        quick.setBackground(round(BLUE, 15));
        quick.setOnClickListener(v -> openSystem(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), null));
        addWithTop(hero, quick, 17);
        root.addView(hero);

        LinearLayout searchArea = col(16, 17, 16, 9);
        TextView choose = tv("Выберите приложение", 19, INK, true);
        searchArea.addView(choose);
        EditText filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setTextSize(15);
        filter.setHint("Поиск по названию или пакету");
        filter.setPadding(dp(15), dp(12), dp(15), dp(12));
        filter.setBackground(round(WHITE, 14, LINE, 1));
        filter.setText(search);
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                search = s.toString();
                refreshList();
            }
            public void afterTextChanged(Editable s) {}
        });
        addWithTop(searchArea, filter, 12);
        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox cb = new CheckBox(this);
        cb.setText("Показывать системные службы");
        cb.setTextSize(13);
        cb.setTextColor(MUTED);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
        cb.setChecked(showSystem);
        cb.setOnCheckedChangeListener((button, checked) -> {
            showSystem = checked;
            refreshList();
        });
        options.addView(cb, new LinearLayout.LayoutParams(0, dp(40), 1));
        countView = tv("", 12, MUTED, false);
        options.addView(countView);
        addWithTop(searchArea, options, 5);
        root.addView(searchArea);

        listView = new ListView(this);
        listView.setBackgroundColor(BG);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(dp(12), 0, dp(12), dp(14));
        appAdapter = new AppAdapter();
        listView.setAdapter(appAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedPackage = appAdapter.items.get(position).pkg;
            showDetails();
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
        refreshList();
    }

    private void refreshList() {
        if (appAdapter == null || listView == null || selectedPackage != null) return;
        String q = search.trim().toLowerCase(Locale.ROOT);
        appAdapter.items.clear();
        for (AppEntry entry : allApps) {
            if (!showSystem && entry.system && !entry.launcher) continue;
            if (!q.isEmpty()
                    && !entry.name.toLowerCase(Locale.ROOT).contains(q)
                    && !entry.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
            appAdapter.items.add(entry);
        }
        appAdapter.notifyDataSetChanged();
        if (countView != null) countView.setText(loaded ? String.valueOf(appAdapter.items.size()) : "…");
    }

    private final class AppAdapter extends BaseAdapter {
        final ArrayList<AppEntry> items = new ArrayList<>();
        @Override public int getCount() { return items.size(); }
        @Override public AppEntry getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View view, ViewGroup parent) {
            AppEntry a = getItem(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round(WHITE, 15, LINE, 1));
            row.setPadding(dp(13), dp(11), dp(12), dp(11));
            ImageView image = new ImageView(MainActivity.this);
            try { image.setImageDrawable(getPackageManager().getApplicationIcon(a.pkg)); }
            catch (PackageManager.NameNotFoundException e) { image.setImageResource(android.R.drawable.sym_def_app_icon); }
            row.addView(image, new LinearLayout.LayoutParams(dp(41), dp(41)));
            LinearLayout textCol = col(0, 0, 0, 0);
            TextView name = tv(a.name, 15, INK, true);
            name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(name);
            TextView pkg = tv(a.pkg, 11, MUTED, false);
            pkg.setSingleLine(true); pkg.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            addWithTop(textCol, pkg, 4);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1);
            textLp.leftMargin = dp(13);
            row.addView(textCol, textLp);
            TextView arrow = tv("›", 27, MUTED, false);
            row.addView(arrow);
            LinearLayout outer = col(4, 4, 4, 5);
            outer.addView(row);
            return outer;
        }
    }

    private void showDetails() {
        root.removeAllViews();
        listView = null;
        appAdapter = null;
        countView = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = col(0, 0, 0, 26);
        scroll.addView(content);
        LinearLayout hero = col(19, 13, 19, 20);
        hero.setBackgroundColor(Color.rgb(18, 26, 49));
        TextView back = tv("‹  Все приложения", 14, Color.rgb(203, 209, 250), true);
        back.setPadding(0, dp(4), 0, dp(12));
        back.setOnClickListener(v -> { selectedPackage = null; showHome(); });
        hero.addView(back);
        LinearLayout appRow = new LinearLayout(this);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(selectedPackage)); }
        catch (Exception e) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(55), dp(55));
        appRow.addView(icon, iconLp);
        LinearLayout texts = col(0, 0, 0, 0);
        TextView appName = tv(appName(selectedPackage), 22, WHITE, true);
        texts.addView(appName);
        TextView pkg = tv(selectedPackage, 12, Color.rgb(200, 210, 232), false);
        pkg.setMaxLines(2);
        addWithTop(texts, pkg, 4);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1);
        textLp.leftMargin = dp(15);
        appRow.addView(texts, textLp);
        hero.addView(appRow);
        batteryStatus = tv(batteryText(selectedPackage), 12, Color.rgb(231, 235, 250), true);
        batteryStatus.setPadding(dp(13), dp(10), dp(13), dp(10));
        batteryStatus.setBackground(round(Color.rgb(47, 59, 92), 11));
        addWithTop(hero, batteryStatus, 15);
        content.addView(hero);

        LinearLayout actions = col(16, 16, 16, 0);
        String pkgName = selectedPackage;
        addSection(actions, "01  Аккумулятор");
        addAction(actions, "Настройки приложения и батареи", "Откройте «Батарея» → «Без ограничений» (название зависит от телефона).", () -> openAppDetails(pkgName));
        addAction(actions, "Исключения из оптимизации батареи", "Общий список Android: найдите нужное приложение и разрешите работу без оптимизации.", () -> openSystem(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), pkgName));
        addAction(actions, "Режим энергосбережения", "Если он включён, фоновая работа может ограничиваться.", () -> openSystem(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), pkgName));

        addSection(actions, "02  Интернет в фоне");
        addAction(actions, "Фоновые данные и без ограничений", "Разрешить мобильный интернет в фоне и работу при экономии трафика.", () -> openSystem(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:" + pkgName)), pkgName));
        addAction(actions, "Расход и экономия трафика", "Общие настройки передачи данных Android.", () -> openSystem(new Intent(Settings.ACTION_DATA_USAGE_SETTINGS), pkgName));

        addSection(actions, "03  Уведомления и запуск");
        addAction(actions, "Уведомления приложения", "Разрешить уведомления, которые необходимы для сообщений и напоминаний.", () -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, pkgName);
            openSystem(i, pkgName);
        });
        addAction(actions, "Автозапуск производителя", "Попробовать открыть настройки автозапуска Xiaomi, OPPO, vivo, Huawei и др. Если недоступны — откроется карточка приложения.", () -> openAutostart(pkgName));
        addAction(actions, "Точные будильники", "Только для программ, использующих точные будильники и напоминания.", () -> openSystem(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkgName)), pkgName));

        addSection(actions, "04  Дополнительный спецдоступ");
        addAction(actions, "Поверх других окон", "Нужен лишь приложениям, показывающим плавающие элементы.", () -> openSystem(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), pkgName));
        addAction(actions, "Доступ к статистике использования", "Нужен только некоторым программам для контроля активности.", () -> openSystem(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), pkgName));
        if (Build.VERSION.SDK_INT >= 34) {
            addAction(actions, "Полноэкранные уведомления", "Для звонков, будильников и других срочных событий.", () -> openSystem(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkgName)), pkgName));
        }
        addAction(actions, "Список всех приложений в Android", "Общий раздел системных настроек.", () -> openSystem(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS), pkgName));

        TextView note = tv("Все кнопки открывают официальные системные окна (кроме попытки открыть фирменный автозапуск). Приложение не выдаёт разрешения самостоятельно. Некоторые пункты могут отсутствовать на вашей прошивке.", 12, MUTED, false);
        note.setPadding(dp(4), dp(16), dp(4), dp(12));
        actions.addView(note);
        content.addView(actions);
    }

    private String appName(String pkg) {
        for (AppEntry a : allApps) if (a.pkg.equals(pkg)) return a.name;
        try { return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))); }
        catch (Exception ignored) { return pkg; }
    }

    private String batteryText(String pkg) {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(pkg)) {
                return "✓  В списке исключений Doze: да";
            }
            return "○  В списке исключений Doze: нет";
        } catch (Exception e) {
            return "Статус исключений Doze недоступен";
        }
    }

    private void openAppDetails(String pkg) {
        openSystem(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + pkg)), null);
    }

    private void openSystem(Intent intent, String fallbackPkg) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException ex) {
            Toast.makeText(this, "Экран недоступен на этом устройстве", Toast.LENGTH_SHORT).show();
            if (fallbackPkg != null) openAppDetails(fallbackPkg);
        }
    }

    private void openAutostart(String fallbackPkg) {
        String vendor = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        List<ComponentName> candidates = new ArrayList<>();
        if (vendor.contains("xiaomi") || vendor.contains("redmi") || vendor.contains("poco")) {
            candidates.add(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        }
        if (vendor.contains("huawei") || vendor.contains("honor")) {
            candidates.add(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            candidates.add(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        }
        if (vendor.contains("oppo") || vendor.contains("realme") || vendor.contains("oneplus")) {
            candidates.add(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            candidates.add(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"));
        }
        if (vendor.contains("vivo") || vendor.contains("iqoo")) {
            candidates.add(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        }
        for (ComponentName c : candidates) {
            try {
                Intent i = new Intent();
                i.setComponent(c);
                startActivity(i);
                return;
            } catch (Exception ignored) {
                // OEM-specific panels are not public Android APIs and may change.
            }
        }
        Toast.makeText(this, "Автозапуск не найден; открываю настройки приложения", Toast.LENGTH_LONG).show();
        openAppDetails(fallbackPkg);
    }

    private void addSection(LinearLayout parent, String title) {
        TextView text = tv(title, 13, BLUE, true);
        text.setPadding(dp(3), dp(21), dp(3), dp(11));
        parent.addView(text);
    }

    private void addAction(LinearLayout parent, String title, String description, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(14), dp(14));
        card.setBackground(round(WHITE, 15, LINE, 1));
        LinearLayout labels = col(0, 0, 0, 0);
        TextView name = tv(title, 15, INK, true);
        labels.addView(name);
        TextView sub = tv(description, 12, MUTED, false);
        addWithTop(labels, sub, 5);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView next = tv("›", 27, BLUE, false);
        LinearLayout.LayoutParams chevron = new LinearLayout.LayoutParams(-2, -2);
        chevron.leftMargin = dp(8);
        card.addView(next, chevron);
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        parent.addView(card, lp);
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout col(int left, int top, int right, int bottom) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        return view;
    }

    private void addWithTop(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeDp) {
        GradientDrawable d = round(color, radius);
        d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
