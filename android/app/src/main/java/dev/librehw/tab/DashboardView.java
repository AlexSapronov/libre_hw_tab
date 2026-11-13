package dev.librehw.tab;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** A native Canvas dashboard: no WebView, remote fonts, or browser dependencies. */
public final class DashboardView extends View {
    private final Paint p = new Paint(3);
    private final Runnable settings, theme;
    private static final class ThermalPoint {
        final long time; final float[] values;
        ThermalPoint(long time,float cpu,float gpu){this.time=time;values=new float[]{cpu,gpu};}
    }
    private final ArrayDeque<ThermalPoint> history = new ArrayDeque<>();
    private JSONObject sample;
    private String error = "Ожидание подключения";
    private boolean demo;
    public int themeId;
    DashboardLayout layout=new DashboardLayout();
    public boolean editing,forceStandby,autoStandby=true,animations=true;
    public JSONObject weather;
    public long wakeSentUntil;
    public String weatherCity="Санкт-Петербург";
    public String weatherHint="Нажми, чтобы обновить";
    public String weatherStatus="Погода загружается…";
    public String nextAlarm="Будильник не задан";
    public Runnable onMenu=()->{},onAlarm=()->{},onWeather=()->{},onWake=()->{},onEditorSave=()->{},onEditorCancel=()->{},onEditorOptions=()->{};
    private long lastGood=android.os.SystemClock.elapsedRealtime(),suppressedUntil,transitionStart;
    private String transitionKey="",selection;
    private boolean nerv,resizing;
    private RectF dragRect;
    private float downX,downY;
    private final Map<String,RectF> drawnCards=new LinkedHashMap<>();
    private final RectF menuHit=new RectF(312,20,396,86);
    private final RectF clockBack=new RectF(36,40,230,95),clockAlarm=new RectF(700,545,1164,635),clockWeather=new RectF(700,250,1164,510),clockWake=new RectF(36,560,600,695);
    public void suppressStandby(){forceStandby=false;suppressedUntil=android.os.SystemClock.elapsedRealtime()+30000;invalidate();}
    public boolean standby(){return !editing && (forceStandby || (autoStandby && sample==null && android.os.SystemClock.elapsedRealtime()-lastGood>=15000 && android.os.SystemClock.elapsedRealtime()>suppressedUntil));}
    public boolean playing(){return mode==2 || (mode==0 && object("game")!=null && object("game").optBoolean("active"));}
    public int mode; // 0 automatic, 1 overview, 2 game
    public Runnable onModeChange = () -> { mode=(mode+1)%3; invalidate(); };
    private final RectF modeHit = new RectF(410,20,654,86);
    private float scale = 1, offsetX, offsetY;
    private int accent, secondary, panel;
    private final int white = Color.rgb(236, 244, 249), muted = Color.rgb(134, 153, 173);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface digits = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final RectF settingsHit = new RectF(1000, 20, 1164, 86), themeHit = new RectF(830, 20, 990, 86);

    public DashboardView(Context context, Runnable settings, Runnable theme) {
        super(context); this.settings = settings; this.theme = theme;
        setContentDescription("Панель мониторинга ПК. Настройки и тема расположены вверху справа.");
        setFocusable(true);
    }
    public void clear() { history.clear(); sample = null; error = "Подключение…"; invalidate(); }
    public void accept(JSONObject data, String error, boolean demo) {
        sample = data; if(data!=null)lastGood=android.os.SystemClock.elapsedRealtime(); this.error = error; this.demo = demo;
        long now=android.os.SystemClock.elapsedRealtime();
        history.add(new ThermalPoint(now,value(object("cpu"),"temperature"),value(object("gpu"),"temperature")));
        while (!history.isEmpty() && (now-history.peekFirst().time>180000 || history.size()>1000))history.removeFirst();
        invalidate();
    }
    private JSONObject object(String key) { return sample == null ? null : sample.optJSONObject(key); }
    private static float value(JSONObject o, String key) {
        double n = o == null || o.isNull(key) ? Double.NaN : o.optDouble(key, Double.NaN);
        return Double.isFinite(n) ? (float)n : Float.NaN;
    }
    private static String number(float n) { return Float.isNaN(n) ? "—" : String.format(Locale.US, "%.0f", n); }
    private static String decimal(float n) { return Float.isNaN(n) ? "—" : String.format(Locale.US, "%.1f", n); }
    private void text(Canvas c, String text, float x, float y, float size, int color, Typeface font) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTypeface(nerv ? (font==medium?Typeface.create("monospace",Typeface.BOLD):Typeface.MONOSPACE) : font); p.setTextSize(size); c.drawText(text, x, y, p);
    }
    private void fit(Canvas c, String text, float x, float y, float size, float width, int color) {
        p.setTypeface(medium); p.setTextSize(size);
        while (p.measureText(text) > width && size > 10) p.setTextSize(--size);
        text(c, text, x, y, size, color, medium);
    }
    private void box(Canvas c, float x, float y, float w, float h, int color) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); if(!nerv)c.drawRoundRect(x,y,x+w,y+h,22,22,p);
        else {
            float cut=Math.min(18,Math.min(w,h)/3);
            Path path=new Path();path.moveTo(x+cut,y);path.lineTo(x+w,y);path.lineTo(x+w,y+h-cut);path.lineTo(x+w-cut,y+h);path.lineTo(x,y+h);path.lineTo(x,y+cut);path.close();c.drawPath(path,p);
            if(h>50){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor((accent&0xffffff)|0x66000000);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        }
    }
    private void line(Canvas c, float x, float y, float endX, float endY, int color, float stroke) {
        p.setShader(null); p.setColor(color); p.setStrokeWidth(stroke); p.setStyle(Paint.Style.STROKE); c.drawLine(x,y,endX,endY,p); p.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        DashboardTheme palette=DashboardTheme.ALL[Math.max(0,Math.min(DashboardTheme.ALL.length-1,themeId))];
        nerv="NERV".equals(palette.name);
        accent=palette.primary;secondary=palette.secondary;panel=palette.panel;
        c.drawColor(palette.background);
        scale = Math.min(getWidth()/1200f, getHeight()/760f);
        offsetX = (getWidth()-1200*scale)/2; offsetY = (getHeight()-760*scale)/2;
        c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
        p.setShader(new RadialGradient(300, 100, 750, palette.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0,0,1200,760,p); p.setShader(null);
        if(standby()){drawStandby(c);finishAnimation(c,"standby");c.restore();postInvalidateDelayed(1000);return;}
        text(c, nerv?"NERV":"LIBRE", 36, 53, 29, white, medium); text(c, "H W   T A B", 144, 52, 16, accent, medium);
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        text(c, time, 700, 53, 27, white, digits);
        boolean playing=playing();
        button(c,menuHit,"МЕНЮ");
        button(c, modeHit, mode==0 ? (playing ? "AUTO / ИГРА" : "AUTO / ОБЗОР") : mode==1 ? "РЕЖИМ / ОБЗОР" : "РЕЖИМ / ИГРА");
        button(c, themeHit, "THEME / "+palette.name); button(c, settingsHit, "ПОДКЛЮЧЕНИЕ");
        boolean healthy = sample != null && sample.isNull("sensorError");
        int status = sample == null ? secondary : healthy ? accent : 0xFFFF7777;
        p.setColor(status); c.drawCircle(43, 93, 4, p);
        String caption = demo ? "DEMO MODE  /  ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ" : sample != null ? sample.optString("host", "PC") + (healthy ? "  /  LIVE TELEMETRY" : "  /  ОШИБКА ДАТЧИКОВ") : "OFFLINE  /  " + error;
        fit(c, caption, 56, 99, 13, 1090, status);
        drawnCards.clear();
        if(playing)drawBlock(c,"main",true,()->gamePanel(c));
        else {
            drawBlock(c,"cpu",false,()->device(c,36,"01","PROCESSOR",object("cpu"),accent,0));
            drawBlock(c,"gpu",false,()->device(c,490,"02","GRAPHICS",object("gpu"),secondary,1));
        }
        drawBlock(c,"side",playing,()->{if(playing)gameHardware(c);else cooling(c);});
        if(layout.metric("memory"))drawBlock(c,"memory",playing,()->memory(c,playing));
        if(layout.metric("history"))drawBlock(c,"history",playing,()->{if(playing)frameGraph(c);else graph(c);});
        if(editing)drawEditor(c,playing);
        finishAnimation(c,(playing?"game":"overview")+themeId);
        JSONObject game = object("game");
        if(sample!=null && !sample.isNull("sensorError"))fit(c,"Ошибка чтения датчиков — проверь агент",36,727,12,1128,0xFFFF7777);
        else if(game!=null && !game.optBoolean("active") && !game.optString("status").matches("idle|ready|starting"))fit(c,"FPS: "+game.optString("status"),36,727,12,1128,muted);
        c.restore();
    }
    private void button(Canvas c, RectF r, String label) {
        box(c, r.left, r.top+7, r.width(), 40, 0xFF152131);
        text(c, label, r.left+14, r.top+33, 12, muted, medium);
    }
    private void gamePanel(Canvas c) {
        box(c,36,125,890,300,panel);
        JSONObject game=object("game");
        text(c,"01   /   GAME TELEMETRY",60,167,13,accent,medium);
        fit(c,game==null ? "Ожидание игры / обнови Windows-агент" : game.isNull("name") ? "Ожидание игры" : game.optString("name","Ожидание игры"),60,204,25,842,white);
        if(layout.metric("fps")){text(c,number(value(game,"fps")),60,337,118,white,digits);text(c,"PRESENTED FPS",64,365,12,accent,medium);}
        if(layout.metric("low1")){text(c,"1% LOW · 10 С",430,246,10,muted,medium);text(c,number(value(game,"low1")),430,307,38,white,digits);text(c,"FPS",430,332,11,muted,medium);}
        if(layout.metric("low01")){text(c,"0,1% LOW · 60 С",590,246,10,muted,medium);text(c,number(value(game,"low01")),590,307,38,white,digits);fit(c,low01Status(game),590,332,10,148,muted);}
        if(layout.metric("frameTime")){text(c,"ВРЕМЯ КАДРА",750,246,10,muted,medium);text(c,decimal(value(game,"frameTime")),750,307,38,white,digits);text(c,"мс",750,332,11,muted,medium);}
        if(game!=null && game.optBoolean("paused"))text(c,"ALT+TAB / ОЖИДАНИЕ КАДРОВ",446,365,11,muted,medium);
    }
    static String low01Status(JSONObject game) {
        if(game==null)return "НЕТ ДАННЫХ";
        if(game.optBoolean("paused"))return "ПАУЗА";
        if(!game.has("low01"))return "ОБНОВИ АГЕНТ ПК";
        if(!Float.isNaN(value(game,"low01")))return "FPS";
        if(game.has("low01Samples")) {
            int samples=game.optInt("low01Samples");double seconds=game.optDouble("low01Seconds",0);
            if(samples<1000)return samples+" / 1000 КАДРОВ";
            return Math.min(10,(int)seconds)+" / 10 СЕКУНД";
        }
        return "НАКОПЛЕНИЕ КАДРОВ";
    }
    private void frameGraph(Canvas c) {
        box(c,324,443,602,247,panel);
        text(c,"FRAME TIME · 60 С",348,476,13,white,medium);
        text(c,"ПИК / 0,2 С",818,476,10,muted,medium);
        JSONObject game=object("game"); JSONArray frames=game==null?null:game.optJSONArray("frameSeries");
        float limit=33.4f;
        if(frames!=null) for(int i=0;i<frames.length();i++) {double v=frames.optDouble(i);if(Double.isFinite(v)&&v>0)limit=Math.max(limit,(float)v);}
        limit=(float)Math.ceil(limit/8.333f)*8.333f;
        float left=348,top=497,width=520,height=155;
        for(int i=0;i<3;i++) {float y=top+i*height/2;line(c,left,y,left+width,y,0xFF263446,1);text(c,number(limit*(1-i/2f)),884,y+4,10,muted,regular);}
        if(frames==null || frames.length()<2) text(c,"Ожидание покадровых данных…",440,610,15,muted,regular);
        else {
            Path path=new Path(); boolean started=false; int count=Math.min(frames.length(),300),start=frames.length()-count;
            for(int i=0;i<count;i++) {
                double v=frames.optDouble(start+i);if(!Double.isFinite(v)||v<=0){started=false;continue;}
                float x=left+width*(300-count+i)/299,y=top+height*(1-(float)v/limit);
                if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            }
            p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        text(c,"−60 с",348,675,10,muted,regular);text(c,"−30 с",587,675,10,muted,regular);text(c,"СЕЙЧАС",821,675,10,muted,regular);
    }
    private void device(Canvas c, float x, String index, String title, JSONObject data, int color, int series) {
        float y = 125;
        box(c,x,y,436,350,panel);
        line(c,x+24,y+26,x+24,y+47,color,3);
        text(c,index+"   /   "+title,x+39,y+42,13,color,medium);
        fit(c,data == null ? "Ожидание датчика" : data.optString("name","—"),x+24,y+78,21,388,white);
        float temp = value(data,"temperature"), load = value(data,"load");
        int thermal = Float.isFinite(temp) && temp >= (series==0?85:83) ? 0xFFFF7777 : color;
        if(layout.metric("temperature")){
        text(c,number(temp),x+24,y+191,82,white,digits);
        p.setTypeface(digits); p.setTextSize(82);
        float unitX = x+24+p.measureText(number(temp))+8;
        text(c,"°C",unitX,y+146,24,thermal,digits);
        text(c,"ТЕМПЕРАТУРА",x+27,y+220,11,muted,medium);
        }
        if(layout.metric("load")){
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFF253245);
        RectF arc = new RectF(x+274,y+120,x+410,y+256); c.drawArc(arc,140,260,false,p);
        if (Float.isFinite(load)) { p.setColor(color); c.drawArc(arc,140,260*Math.max(0,Math.min(1,load/100)),false,p); }
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        loadLabel(c, x+342, y+188, load);
        }
        line(c,x+24,y+266,x+412,y+266,0xFF263446,1);
        if(layout.metric("clock"))stat(c,x+24,y+296,"ЧАСТОТА",number(value(data,"clock"))+" MHz");
        if(layout.metric("power"))stat(c,x+235,y+296,"МОЩНОСТЬ",number(value(data,"power"))+" W");
    }
    private void gameHardware(Canvas c) {
        box(c,944,125,220,565,panel);
        text(c,"02   /   HARDWARE",966,167,13,accent,medium);
        hardwareSection(c,205,"GPU",object("gpu"),secondary,83);
        line(c,966,393,1142,393,0xFF263446,1);
        hardwareSection(c,430,"CPU",object("cpu"),accent,85);

    }
    private void hardwareSection(Canvas c,float y,String title,JSONObject data,int color,float warning) {
        float temperature=value(data,"temperature"),load=value(data,"load");
        int thermal=Float.isFinite(temperature)&&temperature>=warning ? 0xFFFF7777 : color;
        text(c,title,966,y,14,color,medium);
        if(layout.metric("temperature")){valueWithUnit(c,number(temperature),966,y+65,58,"°C",23,18,thermal);
        text(c,"ТЕМПЕРАТУРА",966,y+87,10,muted,medium);}
        if(layout.metric("load")){
        text(c,"ЗАГРУЗКА",966,y+133,10,muted,medium);
        p.setTextAlign(Paint.Align.RIGHT);text(c,number(load)+" %",1142,y+139,31,white,digits);p.setTextAlign(Paint.Align.LEFT);
        box(c,966,y+155,176,6,0xFF263446);
        if(Float.isFinite(load))box(c,966,y+155,176*Math.max(0,Math.min(1,load/100)),6,color);}
    }
    private void cooling(Canvas c) {
        float x=944;
        box(c,x,125,220,565,panel);
        text(c,"04   /   COOLING",x+22,167,13,accent,medium);
        JSONObject cpu=object("cpu"), gpu=object("gpu");
        boolean profile=cpu!=null && cpu.optBoolean("coolingProfile");
        if(layout.metric("fan"))coolingRow(c,x,205,profile ? "ВЕРХ / AIO" : "CPU / AIO",value(cpu,"fan"),profile ? "Fan #1" : sensorName(cpu,"fanName"),accent);
        if(layout.metric("sideFan"))coolingRow(c,x,315,"БОК / ВДУВ",value(cpu,"sideFan"),profile ? "Fan #2" : "КОРПУС",accent);
        if(layout.metric("pump"))coolingRow(c,x,425,"ПОМПА",value(cpu,"pump"),profile ? "Fan #7" : sensorName(cpu,"pumpName"),accent);
        if(layout.metric("gpuFan"))coolingRow(c,x,535,"GPU / ВЕНТИЛЯТОР",value(gpu,"fan"),"ВИДЕОКАРТА",secondary);

    }
    private String sensorName(JSONObject data,String key) {
        return data==null || data.isNull(key) ? "ДАТЧИК НЕДОСТУПЕН" : data.optString(key,"ДАТЧИК НЕДОСТУПЕН");
    }
    private void coolingRow(Canvas c,float x,float y,String title,float rpm,String source,int color) {
        text(c,title,x+22,y,12,color,medium);
        valueWithUnit(c,number(rpm),x+22,y+46,39,"RPM",12,2,muted);
        fit(c,source,x+22,y+67,10,176,muted);
        line(c,x+22,y+86,x+198,y+86,0xFF263446,1);
    }
    private void valueWithUnit(Canvas c,String value,float x,float baseline,float size,String unit,float unitSize,float rise,int color) {
        text(c,value,x,baseline,size,white,digits);
        float unitX=x+p.measureText(value)+10;
        text(c,unit,unitX,baseline-rise,unitSize,color,medium);
    }
    private void loadLabel(Canvas c, float centerX, float centerY, float load) {
        p.setTypeface(digits); p.setTextSize(46); Paint.FontMetrics big = p.getFontMetrics();
        p.setTypeface(medium); p.setTextSize(11); Paint.FontMetrics small = p.getFontMetrics();
        float height = big.descent-big.ascent + 6 + small.descent-small.ascent;
        float top = centerY-height/2;
        p.setTextAlign(Paint.Align.CENTER);
        text(c,number(load),centerX,top-big.ascent,46,white,digits);
        text(c,"% LOAD",centerX,top+big.descent-big.ascent+6-small.ascent,11,muted,medium);
        p.setTextAlign(Paint.Align.LEFT);
    }
    private void stat(Canvas c,float x,float y,String title,String val) { stat(c,x,y,title,val,21); }
    private void stat(Canvas c,float x,float y,String title,String val,float size) {
        text(c,title,x,y,10,muted,medium); text(c,val,x,y+26,size,white,regular);
    }
    private void memory(Canvas c,boolean playing) {
        float top=playing?443:493;
        box(c,36,top,270,690-top,panel);
        text(c,"03   /   MEMORY",60,top+33,13,accent,medium);
        JSONObject ram = object("memory"); float used=value(ram,"used"), total=value(ram,"total"), load=value(ram,"load");
        text(c,decimal(used),60,top+87,42,white,digits);
        text(c,"/ "+number(total)+" GB",183,top+83,20,muted,digits);
        for (int i=0;i<24;i++) box(c,60+i*9.2f,top+107,6,24,Float.isFinite(load) && i<load/100*24 ? accent:0xFF263446);
        text(c,"ИСПОЛЬЗОВАНО",60,661,10,muted,medium);
        p.setTextAlign(Paint.Align.RIGHT); text(c,number(load)+" %",282,661,20,white,regular); p.setTextAlign(Paint.Align.LEFT);
    }
    private void graph(Canvas c) {
        box(c,324,493,602,197,panel);
        text(c,"THERMAL HISTORY",348,526,13,white,medium);
        text(c,"CPU",688,526,11,accent,medium); text(c,"GPU",739,526,11,secondary,medium); text(c,"3 МИН",842,526,11,muted,medium);
        float left=348, top=547, width=520, height=105;
        for(int i=0;i<3;i++) { float y=top+i*height/2; line(c,left,y,left+width,y,0xFF263446,1); text(c,""+(100-i*50),884,y+4,10,muted,regular); }
        for(int series=0;series<2;series++) {
            Path path=new Path(); boolean started=false;
            long now=android.os.SystemClock.elapsedRealtime();
            for(ThermalPoint point:history) {
                float v=point.values[series], x=left+width*(1-(now-point.time)/180000f);
                if(!Float.isFinite(v)) { started=false; continue; }
                float y=top+height*(1-Math.min(100,Math.max(0,v))/100);
                if(!started) { path.moveTo(x,y); started=true; } else path.lineTo(x,y);
            }
            p.setColor(series==0?accent:secondary); p.setStrokeWidth(2.5f); p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path,p); p.setStyle(Paint.Style.FILL);
        }
        text(c,"−180 с",348,675,10,muted,regular); text(c,"−90 с",587,675,10,muted,regular); text(c,"СЕЙЧАС",821,675,10,muted,regular);
    }
    private void drawBlock(Canvas c,String key,boolean game,Runnable renderer) {
        if(!layout.visible(key,game))return;
        RectF source=DashboardLayout.original(key,game),target=layout.rect(key,game);
        drawnCards.put(key,target);
        c.save();c.clipRect(target);box(c,target.left,target.top,target.width(),target.height(),panel);
        float factor=Math.min(target.width()/source.width(),target.height()/source.height());
        c.translate(target.centerX(),target.centerY());c.scale(factor,factor);c.translate(-source.centerX(),-source.centerY());
        renderer.run();c.restore();
    }
    private void drawEditor(Canvas c,boolean game) {
        button(c,modeHit,"РЕДАКТОР / "+(game?"ИГРА":"ОБЗОР"));
        button(c,new RectF(655,20,820,86),"БЛОКИ / ДАННЫЕ");
        button(c,themeHit,"СОХРАНИТЬ");button(c,settingsHit,"ОТМЕНА");
        for(Map.Entry<String,RectF> entry:drawnCards.entrySet()) {
            RectF r=entry.getValue();p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(entry.getKey().equals(selection)?3:1);
            p.setColor(layout.valid(game)?accent:0xFFFF7777);c.drawRect(r,p);p.setStyle(Paint.Style.FILL);
            c.drawRect(r.right-22,r.bottom-22,r.right,r.bottom,p);
        }
        text(c,"Перетаскивай блоки · тяни нижний правый угол · красная рамка — пересечение",36,737,13,muted,medium);
    }
    private void finishAnimation(Canvas c,String key) {
        long now=android.os.SystemClock.elapsedRealtime();
        if(!key.equals(transitionKey)){transitionKey=key;transitionStart=now;}
        if(!nerv)return;
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<80;i++) {
            float x=i*18-18;Path stripe=new Path();stripe.moveTo(x,716);stripe.lineTo(x+8,716);stripe.lineTo(x+18,724);stripe.lineTo(x+10,724);stripe.close();p.setColor(secondary);c.drawPath(stripe,p);
        }
        if(animations && now-transitionStart<450 && !editing) {
            float t=(now-transitionStart)/450f;p.setColor(((int)((1-t)*80)<<24)|(accent&0xffffff));c.drawRect(0,100,1200,710,p);
            line(c,36,125+t*565,1164,125+t*565,accent,2);postInvalidateOnAnimation();
        }
    }
    private void drawStandby(Canvas c) {
        button(c,clockBack,"ПАНЕЛЬ");button(c,settingsHit,"НАСТРОЙКИ");
        java.time.ZonedDateTime now=java.time.ZonedDateTime.now();
        text(c,nerv?"NERV // STANDBY":"LIBRE // STANDBY",60,180,16,accent,medium);
        text(c,now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),52,390,164,white,digits);
        text(c,now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",new Locale("ru"))),60,455,25,muted,regular);
        String city=weather==null?weatherCity:weather.optString("city","Санкт-Петербург");
        fit(c,city,730,265,25,400,white);
        if(weather==null){fit(c,weatherStatus,730,335,22,410,muted);fit(c,weatherHint,730,385,13,410,muted);}
        else {
            valueWithUnit(c,number(value(weather,"temperature")),730,355,78,"°C",24,22,accent);
            fit(c,weather.optString("summary",WeatherClient.description(weather.optInt("code"))),730,408,24,410,muted);
            long age=System.currentTimeMillis()-weather.optLong("fetched",0);
            String source=weather.optString("source","Open-Meteo");
            fit(c,(age>900000?"Сохранено · ":"")+source+" · нажми для обновления",730,447,12,410,muted);
        }
        line(c,730,493,1142,493,0xFF263446,1);
        text(c,"БУДИЛЬНИК  +",730,553,13,accent,medium);fit(c,nextAlarm,730,600,23,410,white);
        boolean waking=android.os.SystemClock.elapsedRealtime()<wakeSentUntil;
        int powerColor=nerv?0xFFFFB52E:accent;
        box(c,32,556,572,143,powerColor);
        box(c,clockWake.left,clockWake.top,clockWake.width(),clockWake.height(),waking?0xFF263C43:powerColor);
        int ink=waking?white:0xFF09121C;
        p.setColor(ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(65,602,115,652),-55,290,false,p);c.drawLine(90,590,90,624,p);
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
        text(c,waking?"КОМАНДА ОТПРАВЛЕНА":"ВКЛЮЧИТЬ ПК",145,618,28,ink,medium);
        text(c,waking?"ЖДУ ПОДКЛЮЧЕНИЯ…":"ВКЛЮЧИТЬ / РАЗБУДИТЬ · WAKE-ON-LAN",145,654,13,ink,regular);
        fit(c,sample==null?"ПК недоступен · "+error:"Соединение с ПК активно",60,738,12,1080,muted);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
        if(editing) {
            if(event.getAction()==MotionEvent.ACTION_DOWN) {
                selection=null;
                for(Map.Entry<String,RectF> e:drawnCards.entrySet())if(e.getValue().contains(x,y)){selection=e.getKey();dragRect=new RectF(e.getValue());downX=x;downY=y;resizing=x>dragRect.right-28 && y>dragRect.bottom-28;}
            } else if(event.getAction()==MotionEvent.ACTION_MOVE && selection!=null) {
                float dx=Math.round((x-downX)/10)*10,dy=Math.round((y-downY)/10)*10;RectF r=new RectF(dragRect);
                if(resizing){r.right=Math.max(r.left+160,Math.min(1164,r.right+dx));r.bottom=Math.max(r.top+110,Math.min(710,r.bottom+dy));}
                else {r.offset(Math.max(36-r.left,Math.min(1164-r.right,dx)),Math.max(125-r.top,Math.min(710-r.bottom,dy)));}
                layout.positions.put(DashboardLayout.id(selection,playing()),r);invalidate();
            } else if(event.getAction()==MotionEvent.ACTION_UP){
                if(themeHit.contains(x,y))onEditorSave.run();else if(settingsHit.contains(x,y))onEditorCancel.run();
                else if(new RectF(655,20,820,86).contains(x,y))onEditorOptions.run();
            }
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_UP) {
            performClick();
            if(standby()) {
                if(clockBack.contains(x,y))suppressStandby();else if(settingsHit.contains(x,y))onMenu.run();
                else if(clockAlarm.contains(x,y))onAlarm.run();else if(clockWeather.contains(x,y))onWeather.run();else if(clockWake.contains(x,y))onWake.run();
            } else if(settingsHit.contains(x,y))settings.run();else if(menuHit.contains(x,y))onMenu.run();
            else if(modeHit.contains(x,y))onModeChange.run();else if(themeHit.contains(x,y))theme.run();
            invalidate();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
