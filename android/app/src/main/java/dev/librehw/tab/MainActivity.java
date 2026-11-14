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
    private ScheduledExecutorService extras;
    private long lastWake;
    private final ExecutorService audioWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService mediaWorker=Executors.newSingleThreadExecutor();
    private volatile boolean mediaBusy,thumbnailBusy;
    private String thumbnailId="";
    private long thumbnailRetry;
    private volatile long nextWeatherAttempt;
    private volatile boolean forceWeather;
    private String weatherError="";
    private int editorPreviousMode;
    private String editorPreviousLayout;
    private final Runnable alarmTick=new Runnable(){public void run(){if(active){updateAlarm();ui.postDelayed(this,30000);}}};

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
        String savedLayout=preferences.getString("layout_v1","{}");
        try{if(new JSONObject(savedLayout).optInt("version")==1 && !preferences.contains("layout_before_audio"))preferences.edit().putString("layout_before_audio",savedLayout).apply();}catch(Exception ignored){}
        dashboard.layout=DashboardLayout.parse(savedLayout);
        dashboard.autoStandby=preferences.getBoolean("auto_standby",true);
        dashboard.animations=preferences.getBoolean("animations",true);
        try{dashboard.weather=new JSONObject(preferences.getString("weather_cache",""));}catch(Exception ignored){}
        dashboard.weatherCity=preferences.getString("weather_city","Санкт-Петербург");
        dashboard.onMenu=this::menu;dashboard.onAlarm=this::alarm;dashboard.onWeather=this::weatherActions;dashboard.onWake=this::wake;
        dashboard.onEditorSave=this::saveEditor;dashboard.onEditorCancel=this::cancelEditor;dashboard.onEditorOptions=this::editorOptions;
        dashboard.onMedia=this::mediaCommand;dashboard.onAudio=this::audioCommand;
        setContentView(dashboard);
        immersive();
        if (address.isEmpty()) ui.post(this::settings);
    }
    @Override protected void onResume() {
        super.onResume(); active=true;restart();ui.post(alarmTick);
        extras=Executors.newSingleThreadScheduledExecutor();if(weatherEnabled())extras.scheduleWithFixedDelay(this::refreshWeather,0,30,TimeUnit.SECONDS);
    }
    @Override protected void onPause() { active=false;stop();ui.removeCallbacks(alarmTick);if(extras!=null)extras.shutdownNow();super.onPause(); }
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
                if(now-lastStart<450)return; // Skip overdue slots after a slow request; never burst.
                lastStart=now; poll(current);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
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
        ui.post(() -> { if(current==generation && active) {
            if(result!=null && result.optJSONArray("wakeTargets")!=null) {
                String targets=result.optJSONArray("wakeTargets").toString();
                if(!targets.equals(preferences.getString("wake_targets","")))preferences.edit().putString("wake_targets",targets).apply();
            }
            dashboard.accept(result,error,demonstration);
            updateThumbnail(result,current);
        }});
    }
    private void updateThumbnail(JSONObject sample,int current) {
        JSONObject media=dashboard.media();String id=media==null?"":media.optString("id");
        if(!id.equals(thumbnailId)){thumbnailId=id;dashboard.mediaThumbnail=null;thumbnailRetry=0;}
        if(media==null||!media.optBoolean("hasThumbnail")||dashboard.mediaThumbnail!=null||thumbnailBusy||demo||android.os.SystemClock.elapsedRealtime()<thumbnailRetry)return;
        thumbnailBusy=true;thumbnailRetry=android.os.SystemClock.elapsedRealtime()+10000;
        String pc=address,key=token;
        mediaWorker.submit(()->{
            android.graphics.Bitmap bitmap=null;try{bitmap=MediaClient.thumbnail(pc,key,id);}catch(Exception ignored){}
            final android.graphics.Bitmap image=bitmap;
            ui.post(()->{thumbnailBusy=false;if(active&&current==generation&&id.equals(thumbnailId)){dashboard.mediaThumbnail=image;dashboard.invalidate();}});
        });
    }
    private void mediaCommand(String action,double fraction) {
        JSONObject media=dashboard.media();if(media==null||mediaBusy||demo)return;
        String id=media.optString("id"),pc=address,key=token;
        double position=Math.max(0,Math.min(1,fraction))*media.optDouble("duration",0);
        mediaBusy=true;dashboard.mediaCommandPending=true;dashboard.invalidate();
        mediaWorker.submit(()->{
            String failure=null;try{MediaClient.control(pc,key,id,action,position);}catch(Exception ex){failure=ex.getMessage();}
            final String message=failure;
            ui.post(()->{mediaBusy=false;dashboard.mediaCommandPending=false;dashboard.invalidate();if(active&&message!=null)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();});
        });
    }
    private void audioCommand(String key,String id,String action,double value) {
        if(demo||dashboard.audioCommandPending)return;
        String pc=address,secret=token;int requestGeneration=generation;
        dashboard.audioCommandPending=true;dashboard.pendingAudioKey=key;dashboard.pendingAudioVolume=action.equals("volume")?value:Double.NaN;dashboard.invalidate();
        audioWorker.submit(()->{
            JSONObject confirmed=null;String error=null;
            try{confirmed=AudioClient.control(pc,secret,key,id,action,value);}catch(Exception ex){error=ex.getMessage();}
            JSONObject result=confirmed;String failure=error;
            ui.post(()->{
                dashboard.audioCommandPending=false;dashboard.pendingAudioKey="";dashboard.pendingAudioVolume=Double.NaN;
                if(active && requestGeneration==generation){if(result!=null)dashboard.audioAcknowledged(result);else if(failure!=null)Toast.makeText(this,failure,Toast.LENGTH_SHORT).show();}
                dashboard.invalidate();
            });
        });
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
    private void menu() {
        new AlertDialog.Builder(this).setTitle("Libre HW Tab")
            .setItems(new String[]{"Экран ожидания","Редактор блоков","Погода: выбрать город","Будильник","Wake-on-LAN","Автоожидание: "+(dashboard.autoStandby?"вкл":"выкл"),"Анимации: "+(dashboard.animations?"вкл":"выкл"),"Подключение"},(d,index)->{
                if(index==0){dashboard.forceStandby=true;dashboard.invalidate();}
                else if(index==1)new AlertDialog.Builder(this).setTitle("Какой макет изменить?").setItems(new String[]{"Обзор","Игра"},(v,choice)->beginEditor(choice==1)).show();
                else if(index==2)weatherCity();else if(index==3)alarm();else if(index==4)wakeOptions();
                else if(index==5){dashboard.autoStandby=!dashboard.autoStandby;preferences.edit().putBoolean("auto_standby",dashboard.autoStandby).apply();dashboard.invalidate();}
                else if(index==6){dashboard.animations=!dashboard.animations;preferences.edit().putBoolean("animations",dashboard.animations).apply();}
                else settings();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void beginEditor(boolean game) {
        editorPreviousMode=dashboard.mode;editorPreviousLayout=dashboard.layout.json();
        dashboard.forceStandby=false;dashboard.mode=game?2:1;dashboard.editing=true;dashboard.invalidate();
    }
    private void saveEditor() {
        if(!dashboard.layout.valid(dashboard.playing())){Toast.makeText(this,"Блоки пересекаются или все скрыты. Раздвинь их перед сохранением.",Toast.LENGTH_LONG).show();return;}
        preferences.edit().putString("layout_v1",dashboard.layout.json()).apply();dashboard.editing=false;dashboard.mode=editorPreviousMode;dashboard.suppressStandby();
    }
    private void cancelEditor(){dashboard.layout=DashboardLayout.parse(editorPreviousLayout);dashboard.editing=false;dashboard.mode=editorPreviousMode;dashboard.suppressStandby();}
    private void editorOptions() {
        new AlertDialog.Builder(this).setTitle("Редактор")
            .setItems(new String[]{"Показать / скрыть блоки","Выбрать показатели","Сбросить оба макета"},(d,index)->{
                if(index==0){
                    boolean game=dashboard.playing();String[] keys=DashboardLayout.keys(game);boolean[] checked=new boolean[keys.length];
                    for(int i=0;i<keys.length;i++)checked[i]=dashboard.layout.visible(keys[i],game);
                    String[] labels=new String[keys.length];for(int i=0;i<keys.length;i++)labels[i]=keys[i].equals("cpu")?"Процессор":keys[i].equals("gpu")?"Видеокарта":keys[i].equals("memory")?"Память":keys[i].equals("history")?"График":keys[i].equals("main")?"Игровые показатели":keys[i].equals("audio")?"Микшер звука":"Правая панель";
                    new AlertDialog.Builder(this).setTitle("Видимые блоки").setMultiChoiceItems(labels,checked,(v,item,on)->{
                        String key=DashboardLayout.id(keys[item],game);if(on)dashboard.layout.hidden.remove(key);else dashboard.layout.hidden.add(key);dashboard.invalidate();
                    }).setPositiveButton("Готово",null).show();
                }else if(index==1){
                    String[] keys={"temperature","load","clock","power","fps","low1","low01","frameTime","memory","history","fan","sideFan","pump","gpuFan"};
                    String[] labels={"Температура","Загрузка","Частота","Мощность","FPS","1% low","0,1% low","Время кадра","Память","График","Верх / AIO","Боковые вентиляторы","Помпа","Вентилятор GPU"};
                    boolean[] checked=new boolean[keys.length];for(int i=0;i<keys.length;i++)checked[i]=dashboard.layout.metric(keys[i]);
                    new AlertDialog.Builder(this).setTitle("Показатели").setMultiChoiceItems(labels,checked,(v,item,on)->{
                        if(on)dashboard.layout.disabledMetrics.remove(keys[item]);else dashboard.layout.disabledMetrics.add(keys[item]);dashboard.invalidate();
                    }).setPositiveButton("Готово",null).show();
                }else{dashboard.layout=new DashboardLayout();dashboard.invalidate();}
            }).show();
    }
    private void updateAlarm() {
        android.app.AlarmManager manager=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);
        android.app.AlarmManager.AlarmClockInfo next=manager.getNextAlarmClock();
        dashboard.nextAlarm=next==null?"Будильник не задан":java.time.Instant.ofEpochMilli(next.getTriggerTime()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, HH:mm",new java.util.Locale("ru")));
        dashboard.invalidate();
    }
    private void alarm() {
        java.time.LocalTime now=java.time.LocalTime.now();
        new android.app.TimePickerDialog(this,(picker,hour,minute)->{
            Intent intent=new Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                .putExtra(android.provider.AlarmClock.EXTRA_HOUR,hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES,minute)
                .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,"Libre HW Tab").putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI,false);
            try{startActivity(intent);}catch(ActivityNotFoundException ex){Toast.makeText(this,"На планшете не найдено приложение Часы",Toast.LENGTH_LONG).show();}
        },now.getHour(),now.getMinute(),true).show();
    }
    protected boolean weatherEnabled(){return true;}
    private void refreshWeather() {
        if(!forceWeather && android.os.SystemClock.elapsedRealtime()<nextWeatherAttempt)return;
        boolean forced=forceWeather;forceWeather=false;
        String city=preferences.getString("weather_city","Санкт-Петербург");
        try {
            JSONObject cached=dashboard.weather;
            long age=cached==null?Long.MAX_VALUE:System.currentTimeMillis()-cached.optLong("fetched");
            if(!forced && cached!=null && cached.optInt("forecastVersion")==2 && city.equals(cached.optString("city")) && age>=0 && age<900000)return;
            double lat=Double.parseDouble(preferences.getString("weather_lat","59.9386")),lon=Double.parseDouble(preferences.getString("weather_lon","30.3141"));
            JSONObject data=WeatherClient.fetch(city,lat,lon,preferences.getString("weather_zone",cached!=null?WeatherClient.forecastZone(cached).getId():city.equals("Санкт-Петербург")?"Europe/Moscow":java.time.ZoneId.systemDefault().getId()));
            if(!city.equals(preferences.getString("weather_city","Санкт-Петербург")))return;
            nextWeatherAttempt=0;
            preferences.edit().putString("weather_cache",data.toString()).putString("weather_zone",data.optString("forecastZone",preferences.getString("weather_zone","Europe/Moscow"))).apply();
            ui.post(()->{if(!isDestroyed()){dashboard.weather=data;weatherError="";dashboard.weatherHint="Нажми, чтобы обновить";dashboard.invalidate();}});
        }catch(Exception ex){
            nextWeatherAttempt=android.os.SystemClock.elapsedRealtime()+30000;
            String reason=WeatherClient.reason(ex);
            ui.post(()->{if(!isDestroyed() && city.equals(preferences.getString("weather_city","Санкт-Петербург"))){weatherError=reason;dashboard.weatherStatus="Нет связи с погодой";dashboard.weatherHint="Повтор через 30 с · нажми для деталей";dashboard.invalidate();}});
        }
    }
    private void weatherActions() {
        new AlertDialog.Builder(this).setTitle("Погода")
            .setMessage(weatherError.isEmpty()?"Обновляется с планшета, даже когда ПК выключен.":weatherError)
            .setPositiveButton("Обновить",(d,w)->requestWeather())
            .setNeutralButton("Город",(d,w)->weatherCity()).setNegativeButton("Закрыть",null).show();
    }
    private void requestWeather() {
        if(extras==null||extras.isShutdown())return;
        dashboard.weatherCity=preferences.getString("weather_city","Санкт-Петербург");
        forceWeather=true;nextWeatherAttempt=0;dashboard.weatherStatus="Погода загружается…";dashboard.weatherHint="Запрашиваю данные…";dashboard.invalidate();
        extras.submit(this::refreshWeather);
    }
    private void weatherCity() {
        EditText city=new EditText(this);city.setSingleLine();city.setText(preferences.getString("weather_city","Санкт-Петербург"));
        new AlertDialog.Builder(this).setTitle("Город для погоды").setView(city).setPositiveButton("Найти",(dialog,w)->{
            String query=city.getText().toString().trim();if(query.length()<2)return;
            Toast.makeText(this,"Поиск города…",Toast.LENGTH_SHORT).show();
            pairingWorker.submit(()->{
                try {
                    JSONArray cities=WeatherClient.cities(query);String[] names=new String[cities.length()];
                    for(int i=0;i<names.length;i++){JSONObject item=cities.getJSONObject(i);names[i]=item.getString("name")+", "+item.optString("admin1")+", "+item.optString("country");}
                    ui.post(()->{if(!active)return;if(names.length==0){Toast.makeText(this,"Город не найден",Toast.LENGTH_LONG).show();return;}
                        new AlertDialog.Builder(this).setTitle("Выбери город").setItems(names,(v,index)->{
                            try{JSONObject selected=cities.getJSONObject(index);preferences.edit().putString("weather_city",selected.getString("name")).putString("weather_lat",Double.toString(selected.getDouble("latitude"))).putString("weather_lon",Double.toString(selected.getDouble("longitude"))).putString("weather_zone",selected.optString("timezone",java.time.ZoneId.systemDefault().getId())).remove("weather_cache").apply();dashboard.weather=null;dashboard.weatherStatus="Погода загружается…";requestWeather();}catch(Exception ignored){}
                        }).show();
                    });
                }catch(Exception ex){ui.post(()->Toast.makeText(this,"Поиск недоступен. Проверь интернет планшета.",Toast.LENGTH_LONG).show());}
            });
        }).setNeutralButton("Open-Meteo",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")));}catch(ActivityNotFoundException ignored){}}).setNegativeButton("Отмена",null).show();
    }
    private void wake() {
        long now=android.os.SystemClock.elapsedRealtime();if(lastWake>0 && now-lastWake<10000)return;lastWake=now;dashboard.wakeSentUntil=now+10000;dashboard.invalidate();
        pairingWorker.submit(()->{
            try{WakeClient.send(new JSONArray(preferences.getString("wake_targets","[]")));ui.post(()->{dashboard.forceStandby=false;dashboard.invalidate();Toast.makeText(this,"Команда отправлена. Жду подключения к ПК…",Toast.LENGTH_LONG).show();});}
            catch(Exception ex){ui.post(()->{lastWake=0;dashboard.wakeSentUntil=0;dashboard.invalidate();Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();});}
        });
    }
    private void wakeOptions() {
        new AlertDialog.Builder(this).setTitle("Wake-on-LAN")
            .setMessage("Пробуждение требует включённого Wake-on-LAN в BIOS и настройках сетевой карты. Из полного выключения поддержка зависит от платы. ПК должен быть подключён к питанию; надёжнее Ethernet. Команда отправляется только по домашней сети.")
            .setPositiveButton("Отправить команду",(d,w)->wake()).setNeutralButton("Указать MAC",(d,w)->{
                EditText mac=new EditText(this);mac.setSingleLine();mac.setHint("AA:BB:CC:DD:EE:FF");
                AlertDialog input=new AlertDialog.Builder(this).setTitle("MAC сетевой карты ПК").setView(mac).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
                input.setOnShowListener(v->input.getButton(-1).setOnClickListener(b->{try{WakeClient.packet(mac.getText().toString());JSONArray targets=new JSONArray().put(new JSONObject().put("mac",mac.getText().toString()).put("broadcast","255.255.255.255"));preferences.edit().putString("wake_targets",targets.toString()).apply();input.dismiss();}catch(Exception ex){mac.setError(ex.getMessage());}}));input.show();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void cancelPairing() { pairingGeneration++; if (pairingTask != null) pairingTask.cancel(true); }
    @Override protected void onDestroy() {
        cancelPairing(); pairingWorker.shutdownNow();mediaWorker.shutdownNow();audioWorker.shutdownNow();
        if (pairingDialog != null) pairingDialog.dismiss();
        super.onDestroy();
    }

}
