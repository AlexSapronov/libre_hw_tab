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

public class MainActivity extends Activity {
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
        dashboard.eva = preferences.getBoolean("eva", false);
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
        dashboard.eva = !dashboard.eva;
        preferences.edit().putBoolean("eva", dashboard.eva).apply();
        dashboard.invalidate();
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
        worker.scheduleWithFixedDelay(() -> poll(current), 0, 1, TimeUnit.SECONDS);
    }
    private void poll(int current) {
        JSONObject sample = null;
        String message = "Укажи адрес компьютера в настройках";
        boolean demonstration = demo;
        HttpURLConnection request = null;
        try {
            if (demonstration) sample = DemoData.sample();
            else if (!address.isEmpty()) {
                request = (HttpURLConnection) new URL(address + "/api/v1/telemetry").openConnection();
                synchronized (this) { if (current != generation) return; connection = request; }
                request.setConnectTimeout(2500); request.setReadTimeout(2500);
                request.setInstanceFollowRedirects(false);
                request.setRequestProperty("X-Libre-Token", token);
                int status = request.getResponseCode();
                if (status == 401) throw new IOException("Неверный ключ подключения");
                if (status != 200) throw new IOException("Агент: HTTP " + status);
                try (InputStream input = request.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 65536) throw new IOException("Слишком большой ответ агента");
                        output.write(buffer, 0, count);
                    }
                    sample = new JSONObject(output.toString("UTF-8"));
                    if (sample.optInt("version") != 1 || !sample.has("timestamp")) throw new IOException("Несовместимая версия агента");
                    long age = Math.abs(java.time.Duration.between(java.time.Instant.parse(sample.getString("timestamp")), java.time.Instant.now()).getSeconds());
                    if (age > 10) throw new IOException("Данные устарели. Проверь часы ПК и планшета");
                }
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
        TextView info = new TextView(this); info.setText("ПК и планшет должны быть в одной домашней сети. Адрес и ключ покажет установщик Windows.\n\nНажми THEME для смены оформления."); layout.addView(info);
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
        dialog.show();
    }
}
