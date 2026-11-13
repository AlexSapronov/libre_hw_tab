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
    private final ArrayDeque<float[]> history = new ArrayDeque<>();
    private JSONObject sample;
    private String error = "Ожидание подключения";
    private boolean demo;
    public boolean eva;
    public int mode; // 0 automatic, 1 overview, 2 game
    public Runnable onModeChange = () -> { mode=(mode+1)%3; invalidate(); };
    private final RectF modeHit = new RectF(410,20,654,86);
    private float scale = 1, offsetX, offsetY;
    private int accent, secondary;
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
        sample = data; this.error = error; this.demo = demo;
        history.add(new float[] {value(object("cpu"), "temperature"), value(object("gpu"), "temperature")});
        while (history.size() > 180) history.removeFirst();
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
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTypeface(font); p.setTextSize(size); c.drawText(text, x, y, p);
    }
    private void fit(Canvas c, String text, float x, float y, float size, float width, int color) {
        p.setTypeface(medium); p.setTextSize(size);
        while (p.measureText(text) > width && size > 10) p.setTextSize(--size);
        text(c, text, x, y, size, color, medium);
    }
    private void box(Canvas c, float x, float y, float w, float h, int color) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(x, y, x+w, y+h, 22, 22, p);
    }
    private void line(Canvas c, float x, float y, float endX, float endY, int color, float stroke) {
        p.setShader(null); p.setColor(color); p.setStrokeWidth(stroke); p.setStyle(Paint.Style.STROKE); c.drawLine(x,y,endX,endY,p); p.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        accent = Color.parseColor(eva ? "#BCFF51" : "#5EF2CA"); secondary = Color.parseColor(eva ? "#B598FF" : "#FFBA75");
        c.drawColor(Color.rgb(7, 12, 21));
        scale = Math.min(getWidth()/1200f, getHeight()/760f);
        offsetX = (getWidth()-1200*scale)/2; offsetY = (getHeight()-760*scale)/2;
        c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
        p.setShader(new RadialGradient(300, 100, 750, eva ? 0x252E165B : 0x25205049, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0,0,1200,760,p); p.setShader(null);
        text(c, "LIBRE", 36, 53, 29, white, medium); text(c, "H W   T A B", 144, 52, 16, accent, medium);
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        text(c, time, 700, 53, 27, white, digits);
        boolean playing = mode==2 || (mode==0 && object("game")!=null && object("game").optBoolean("active"));
        button(c, modeHit, mode==0 ? (playing ? "AUTO / ИГРА" : "AUTO / ОБЗОР") : mode==1 ? "РЕЖИМ / ОБЗОР" : "РЕЖИМ / ИГРА");
        button(c, themeHit, eva ? "THEME / EVA" : "THEME / JADE"); button(c, settingsHit, "ПОДКЛЮЧЕНИЕ");
        boolean healthy = sample != null && sample.isNull("sensorError");
        int status = sample == null ? secondary : healthy ? accent : 0xFFFF7777;
        p.setColor(status); c.drawCircle(43, 93, 4, p);
        String caption = demo ? "DEMO MODE  /  ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ" : sample != null ? sample.optString("host", "PC") + (healthy ? "  /  LIVE TELEMETRY" : "  /  ОШИБКА ДАТЧИКОВ") : "OFFLINE  /  " + error;
        fit(c, caption, 56, 99, 13, 1090, status);
        if (playing) gamePanel(c);
        else {
            device(c, 36, "01", "PROCESSOR", object("cpu"), accent, 0);
            device(c, 490, "02", "GRAPHICS", object("gpu"), secondary, 1);
        }
        if (playing) gameHardware(c); else cooling(c);
        memory(c);
        if (playing) frameGraph(c); else graph(c);
        text(c, "SYSTEM TELEMETRY", 36, 727, 11, muted, medium);
        String footer = sample != null && !sample.isNull("sensorError") ? "Ошибка чтения датчиков — проверь агент и права администратора" : "Обновление 1 с   ·   Температура, загрузка и частоты — доступные датчики ПК";
        JSONObject game = object("game");
        if(game!=null && !game.optBoolean("active") && !game.optString("status").matches("idle|ready|starting")) footer="FPS: "+game.optString("status");
        fit(c, footer, 250, 727, 12, 910, muted);
        c.restore();
    }
    private void button(Canvas c, RectF r, String label) {
        box(c, r.left, r.top+7, r.width(), 40, 0xFF152131);
        text(c, label, r.left+14, r.top+33, 12, muted, medium);
    }
    private void gamePanel(Canvas c) {
        box(c,36,125,890,350,0xFF101B29);
        JSONObject game=object("game");
        text(c,"01   /   GAME TELEMETRY",60,167,13,accent,medium);
        fit(c,game==null ? "Ожидание игры / обнови Windows-агент" : game.isNull("name") ? "Ожидание игры" : game.optString("name","Ожидание игры"),60,204,25,842,white);
        text(c,number(value(game,"fps")),60,337,118,white,digits);
        text(c,"PRESENTED FPS",64,365,12,accent,medium);
        text(c,"1% LOW · 10 С",446,246,11,muted,medium);
        text(c,"СРЕДНЕЕ ВРЕМЯ КАДРА",680,246,11,muted,medium);
        text(c,number(value(game,"low1")),446,307,43,white,digits);
        text(c,decimal(value(game,"frameTime")),680,307,43,white,digits);
        text(c,"FPS",446,332,11,muted,medium); text(c,"мс",680,332,11,muted,medium);
        String status=game!=null && game.optBoolean("paused") ? "ALT+TAB / ОЖИДАНИЕ КАДРОВ" : "PRESENTMON / ПОКАДРОВЫЕ ИЗМЕРЕНИЯ";
        text(c,status,446,365,11,muted,medium);
        line(c,60,394,902,394,0xFF263446,1);
        text(c,"FPS И ВРЕМЯ КАДРА: 1 СЕКУНДА   ·   1% LOW: 10 СЕКУНД",60,440,12,muted,medium);
    }
    private void frameGraph(Canvas c) {
        box(c,324,493,602,197,0xFF101B29);
        text(c,"FRAME TIME",348,526,13,white,medium);
        text(c,"ПОСЛЕДНИЕ 180 КАДРОВ",742,526,10,muted,medium);
        JSONObject game=object("game"); JSONArray frames=game==null?null:game.optJSONArray("frames");
        float limit=33.4f;
        if(frames!=null) for(int i=0;i<frames.length();i++) {double v=frames.optDouble(i);if(Double.isFinite(v)&&v>0)limit=Math.max(limit,(float)v);}
        limit=(float)Math.ceil(limit/8.333f)*8.333f;
        float left=348,top=547,width=520,height=105;
        for(int i=0;i<3;i++) {float y=top+i*height/2;line(c,left,y,left+width,y,0xFF263446,1);text(c,number(limit*(1-i/2f)),884,y+4,10,muted,regular);}
        if(frames==null || frames.length()<2) text(c,"Ожидание покадровых данных…",440,610,15,muted,regular);
        else {
            Path path=new Path(); boolean started=false; int count=Math.min(frames.length(),180),start=frames.length()-count;
            for(int i=0;i<count;i++) {
                double v=frames.optDouble(start+i);if(!Double.isFinite(v)||v<=0){started=false;continue;}
                float x=left+width*i/Math.max(1,count-1),y=top+height*(1-(float)v/limit);
                if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            }
            p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        text(c,"мс / кадр",348,675,10,muted,regular);text(c,"СЕЙЧАС",821,675,10,muted,regular);
    }
    private void device(Canvas c, float x, String index, String title, JSONObject data, int color, int series) {
        float y = 125;
        box(c,x,y,436,350,0xFF101B29);
        line(c,x+24,y+26,x+24,y+47,color,3);
        text(c,index+"   /   "+title,x+39,y+42,13,color,medium);
        fit(c,data == null ? "Ожидание датчика" : data.optString("name","—"),x+24,y+78,21,388,white);
        float temp = value(data,"temperature"), load = value(data,"load");
        int thermal = Float.isFinite(temp) && temp >= (series==0?85:83) ? 0xFFFF7777 : color;
        text(c,number(temp),x+24,y+191,82,white,digits);
        p.setTypeface(digits); p.setTextSize(82);
        float unitX = x+24+p.measureText(number(temp))+8;
        text(c,"°C",unitX,y+146,24,thermal,digits);
        text(c,"ТЕМПЕРАТУРА",x+27,y+220,11,muted,medium);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFF253245);
        RectF arc = new RectF(x+274,y+120,x+410,y+256); c.drawArc(arc,140,260,false,p);
        if (Float.isFinite(load)) { p.setColor(color); c.drawArc(arc,140,260*Math.max(0,Math.min(1,load/100)),false,p); }
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        loadLabel(c, x+342, y+188, load);
        line(c,x+24,y+266,x+412,y+266,0xFF263446,1);
        stat(c,x+24,y+296,"ЧАСТОТА",number(value(data,"clock"))+" MHz");
        stat(c,x+235,y+296,"МОЩНОСТЬ",number(value(data,"power"))+" W");
    }
    private void gameHardware(Canvas c) {
        box(c,944,125,220,565,0xFF101B29);
        text(c,"02   /   HARDWARE",966,167,13,accent,medium);
        hardwareSection(c,205,"GPU",object("gpu"),secondary,83);
        line(c,966,393,1142,393,0xFF263446,1);
        hardwareSection(c,430,"CPU",object("cpu"),accent,85);
        text(c,"ТЕМПЕРАТУРА / ЗАГРУЗКА",966,669,10,muted,medium);
    }
    private void hardwareSection(Canvas c,float y,String title,JSONObject data,int color,float warning) {
        float temperature=value(data,"temperature"),load=value(data,"load");
        int thermal=Float.isFinite(temperature)&&temperature>=warning ? 0xFFFF7777 : color;
        text(c,title,966,y,14,color,medium);
        text(c,number(temperature),966,y+65,58,white,digits);
        text(c,"°C",1091,y+47,23,thermal,digits);
        text(c,"ТЕМПЕРАТУРА",966,y+87,10,muted,medium);
        text(c,"ЗАГРУЗКА",966,y+133,10,muted,medium);
        p.setTextAlign(Paint.Align.RIGHT);text(c,number(load)+" %",1142,y+139,31,white,digits);p.setTextAlign(Paint.Align.LEFT);
        box(c,966,y+155,176,6,0xFF263446);
        if(Float.isFinite(load))box(c,966,y+155,176*Math.max(0,Math.min(1,load/100)),6,color);
    }
    private void cooling(Canvas c) {
        float x=944;
        box(c,x,125,220,565,0xFF101B29);
        text(c,"04   /   COOLING",x+22,167,13,accent,medium);
        JSONObject cpu=object("cpu"), gpu=object("gpu");
        boolean profile=cpu!=null && cpu.optBoolean("coolingProfile");
        coolingRow(c,x,205,profile ? "ВЕРХ / AIO" : "CPU / AIO",value(cpu,"fan"),profile ? "Fan #1" : sensorName(cpu,"fanName"),accent);
        coolingRow(c,x,315,"БОК / ВДУВ",value(cpu,"sideFan"),profile ? "Fan #2" : "КОРПУС",accent);
        coolingRow(c,x,425,"ПОМПА",value(cpu,"pump"),profile ? "Fan #7" : sensorName(cpu,"pumpName"),accent);
        coolingRow(c,x,535,"GPU / ВЕНТИЛЯТОР",value(gpu,"fan"),"ВИДЕОКАРТА",secondary);
        text(c,"ТОЛЬКО МОНИТОРИНГ",x+22,669,10,muted,medium);
    }
    private String sensorName(JSONObject data,String key) {
        return data==null || data.isNull(key) ? "ДАТЧИК НЕДОСТУПЕН" : data.optString(key,"ДАТЧИК НЕДОСТУПЕН");
    }
    private void coolingRow(Canvas c,float x,float y,String title,float rpm,String source,int color) {
        text(c,title,x+22,y,12,color,medium);
        text(c,number(rpm),x+22,y+46,39,white,digits);
        text(c,"RPM",x+165,y+44,12,muted,medium);
        fit(c,source,x+22,y+67,10,176,muted);
        line(c,x+22,y+86,x+198,y+86,0xFF263446,1);
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
    private void memory(Canvas c) {
        box(c,36,493,270,197,0xFF101B29);
        text(c,"03   /   MEMORY",60,526,13,accent,medium);
        JSONObject ram = object("memory"); float used=value(ram,"used"), total=value(ram,"total"), load=value(ram,"load");
        text(c,decimal(used),60,580,42,white,digits);
        text(c,"/ "+number(total)+" GB",183,576,20,muted,digits);
        for (int i=0;i<24;i++) box(c,60+i*9.2f,600,6,24,Float.isFinite(load) && i<load/100*24 ? accent:0xFF263446);
        text(c,"ИСПОЛЬЗОВАНО",60,661,10,muted,medium);
        p.setTextAlign(Paint.Align.RIGHT); text(c,number(load)+" %",282,661,20,white,regular); p.setTextAlign(Paint.Align.LEFT);
    }
    private void graph(Canvas c) {
        box(c,324,493,602,197,0xFF101B29);
        text(c,"THERMAL HISTORY",348,526,13,white,medium);
        text(c,"CPU",688,526,11,accent,medium); text(c,"GPU",739,526,11,secondary,medium); text(c,"3 МИН",842,526,11,muted,medium);
        float left=348, top=547, width=520, height=105;
        for(int i=0;i<3;i++) { float y=top+i*height/2; line(c,left,y,left+width,y,0xFF263446,1); text(c,""+(100-i*50),884,y+4,10,muted,regular); }
        for(int series=0;series<2;series++) {
            Path path=new Path(); boolean started=false; int i=180-history.size();
            for(float[] point:history) {
                float v=point[series], x=left+width*i++/179;
                if(!Float.isFinite(v)) { started=false; continue; }
                float y=top+height*(1-Math.min(100,Math.max(0,v))/100);
                if(!started) { path.moveTo(x,y); started=true; } else path.lineTo(x,y);
            }
            p.setColor(series==0?accent:secondary); p.setStrokeWidth(2.5f); p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path,p); p.setStyle(Paint.Style.FILL);
        }
        text(c,"−180 с",348,675,10,muted,regular); text(c,"−90 с",587,675,10,muted,regular); text(c,"СЕЙЧАС",821,675,10,muted,regular);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_UP) {
            float x=(event.getX()-offsetX)/scale, y=(event.getY()-offsetY)/scale;
            if(settingsHit.contains(x,y)) { performClick(); settings.run(); }
            else if(modeHit.contains(x,y)) { performClick(); onModeChange.run(); }
            else if(themeHit.contains(x,y)) { performClick(); theme.run(); }
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
