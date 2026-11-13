package dev.librehw.tab;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends androidx.activity.ComponentActivity {
    private final ExecutorService pairingWorker = Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> pairingTask;
    private int pairingGeneration;
    private AlertDialog pairingDialog;
    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> scanner =
        registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> {
            if (result.getContents() != null) importPairing(result.getContents());
        });
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private DashboardView dashboard;
    private volatile String address = "", token = "";
    private volatile boolean demo;
    private int generation;
    private boolean active;
    private android.content.SharedPreferences preferences;
    private HttpURLConnection connection;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences("connection", MODE_PRIVATE);
        address = preferences.getString("address", "");
        token = preferences.getString("token", "");
        dashboard = new DashboardView(this, this::settings, this::toggleTheme);
        dashboard.themeId = preferences.getInt("theme_id",preferences.getBoolean("eva", false)?1:0);
        dashboard.mode = Math.max(0, Math.min(2, preferences.getInt("mode", 0)));
        dashboard.onModeChange = () -> {
            dashboard.mode=(dashboard.mode+1)%3;
            preferences.edit().putInt("mode",dashboard.mode).apply(); dashboard.invalidate();
        };
        setContentView(dashboard);
        immersive();
        if (address.isEmpty()) ui.post(this::settings);
    }
    @Override protected void onResume() { super.onResume(); active = true; restart(); }
    @Override protected void onPause() { active = false; stop(); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void toggleTheme() {
        String[] labels = new String[DashboardTheme.ALL.length];
        for(int i=0;i<labels.length;i++)labels[i]=DashboardTheme.ALL[i].description;
        new AlertDialog.Builder(this).setTitle("Оформление")
            .setSingleChoiceItems(labels,dashboard.themeId,(dialog,which)->{
                dashboard.themeId=which; preferences.edit().putInt("theme_id",which).apply();
                dashboard.invalidate(); dialog.dismiss();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void stop() {
        generation++;
        if (worker != null) worker.shutdownNow();
        synchronized (this) { if (connection != null) connection.disconnect(); connection = null; }
    }
    private void restart() {
        stop();
        if (!active) return;
        final int current = generation;
        worker = Executors.newSingleThreadScheduledExecutor();
        worker.scheduleAtFixedRate(new Runnable() {
            private long lastStart=-1000;
            public void run() {
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-lastStart<180)return; // Skip overdue slots after a slow request; never burst.
                lastStart=now; poll(current);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
    private void poll(int current) {
        JSONObject sample = null;
        String message = "Укажи адрес компьютера в настройках";
        boolean demonstration = demo;
        HttpURLConnection request = null;
        try {
            if (demonstration) sample = DemoData.sample();
            else if (!address.isEmpty()) {
                request = TelemetryClient.open(address, token, 2500);
                synchronized (this) { if (current != generation) return; connection = request; }
                sample = TelemetryClient.read(request);
            }
        } catch (Exception ex) { sample = null; message = ex.getMessage() == null ? "Нет связи с ПК" : ex.getMessage(); }
        finally {
            if (request != null) request.disconnect();
            synchronized (this) { if (connection == request) connection = null; }
        }
        final JSONObject result = sample;
        final String error = message;
        ui.post(() -> { if (current == generation && active) dashboard.accept(result, error, demonstration); });
    }
    private EditText field(LinearLayout layout, String hint, String value, boolean secret) {
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setHint(hint); edit.setText(value);
        edit.setInputType(secret ? 129 : 17);
        layout.addView(edit, new LinearLayout.LayoutParams(-1, -2)); return edit;
    }
    private void settings() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 12, 32, 12);
        TextView info = new TextView(this); info.setText("На компьютере открой Pair.cmd. Нажми «Сканировать QR» и наведи камеру на код — адрес и ключ подставятся сами.\n\nРучной ввод можно использовать как запасной вариант."); layout.addView(info);
        Button scan = new Button(this); scan.setText("Сканировать QR"); layout.addView(scan);
        EditText host = field(layout, "http://192.168.1.10:8750", address, false);
        EditText key = field(layout, "Ключ подключения", token, true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Подключение к компьютеру").setView(layout)
            .setPositiveButton("Подключить", null)
            .setNeutralButton("Демо", (d, w) -> { demo = true; dashboard.clear(); restart(); })
            .setNegativeButton("Закрыть", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                String raw = host.getText().toString().trim();
                if (!raw.contains("://")) raw = "http://" + raw;
                URI uri = new URI(raw);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new Exception();
                if (key.getText().toString().trim().length() < 16) { key.setError("Вставь полный ключ из установщика"); return; }
                address = raw.replaceAll("/+$", ""); token = key.getText().toString().trim(); demo = false;
                preferences.edit().putString("address", address).putString("token", token).apply();
                dashboard.clear(); restart(); dialog.dismiss();
            } catch (Exception ex) { host.setError("Нужен адрес вида http://192.168.1.10:8750"); }
        }));
        scan.setOnClickListener(v -> {
            dialog.dismiss();
            scanner.launch(new com.journeyapps.barcodescanner.ScanOptions()
                .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                .setPrompt("Наведи камеру на QR в окне Libre HW Tab на компьютере")
                .setOrientationLocked(false).setBeepEnabled(false));
        });
        dialog.show();
    }
    private void importPairing(String text) {
        final PairingInfo info;
        try { info = PairingInfo.parse(text); }
        catch (Exception ex) { new AlertDialog.Builder(this).setTitle("QR не подходит").setMessage(ex.getMessage()).setPositiveButton("ОК",null).show(); return; }
        final int attempt = ++pairingGeneration;
        if (pairingTask != null) pairingTask.cancel(true);
        pairingDialog = new AlertDialog.Builder(this).setTitle("Подключаемся к ПК")
            .setMessage("Проверяю адреса из QR. Ничего вводить не нужно…")
            .setNegativeButton("Отмена", (d,w) -> cancelPairing()).create();
        pairingDialog.setOnCancelListener(d -> cancelPairing());
        pairingDialog.show();
        pairingTask = pairingWorker.submit(() -> {
            String selected = null, problem = null;
            try { selected = info.reachable(TelemetryClient::probe); }
            catch (Exception ex) { problem = ex.getMessage(); }
            final String host = selected, error = problem;
            ui.post(() -> {
                if (isDestroyed() || attempt != pairingGeneration) return;
                pairingDialog.dismiss(); pairingDialog = null;
                if (host == null) {
                    new AlertDialog.Builder(this).setTitle("Не удалось подключиться").setMessage(error)
                        .setPositiveButton("Повторить", (d,w) -> importPairing(text)).setNegativeButton("Закрыть", null).show();
                    return;
                }
                address = host; token = info.token; demo = false;
                preferences.edit().putString("address",address).putString("token",token).apply();
                dashboard.clear(); restart();
                Toast.makeText(this,"Подключено к компьютеру",Toast.LENGTH_SHORT).show();
            });
        });
    }
    private void cancelPairing() { pairingGeneration++; if (pairingTask != null) pairingTask.cancel(true); }
    @Override protected void onDestroy() {
        cancelPairing(); pairingWorker.shutdownNow();
        if (pairingDialog != null) pairingDialog.dismiss();
        super.onDestroy();
    }

}
