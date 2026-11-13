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
        button(c, themeHit, eva ? "THEME / EVA" : "THEME / JADE"); button(c, settingsHit, "ПОДКЛЮЧЕНИЕ");
        boolean healthy = sample != null && sample.isNull("sensorError");
        int status = sample == null ? secondary : healthy ? accent : 0xFFFF7777;
        p.setColor(status); c.drawCircle(43, 93, 4, p);
        String caption = demo ? "DEMO MODE  /  ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ" : sample != null ? sample.optString("host", "PC") + (healthy ? "  /  LIVE TELEMETRY" : "  /  ОШИБКА ДАТЧИКОВ") : "OFFLINE  /  " + error;
        fit(c, caption, 56, 99, 13, 1090, status);
        device(c, 36, "01", "PROCESSOR", object("cpu"), accent, 0);
        device(c, 610, "02", "GRAPHICS", object("gpu"), secondary, 1);
        memory(c);
        graph(c);
        text(c, "SYSTEM TELEMETRY", 36, 727, 11, muted, medium);
        String footer = sample != null && !sample.isNull("sensorError") ? "Ошибка чтения датчиков — проверь агент и права администратора" : "Обновление 1 с   ·   Температура, загрузка и частоты — доступные датчики ПК";
        fit(c, footer, 250, 727, 12, 910, muted);
        c.restore();
    }
    private void button(Canvas c, RectF r, String label) {
        box(c, r.left, r.top+7, r.width(), 40, 0xFF152131);
        text(c, label, r.left+14, r.top+33, 12, muted, medium);
    }
    private void device(Canvas c, float x, String index, String title, JSONObject data, int color, int series) {
        float y = 125;
        box(c,x,y,554,326,0xFF101B29);
        line(c,x+24,y+26,x+24,y+47,color,3);
        text(c,index+"   /   "+title,x+39,y+42,13,color,medium);
        fit(c,data == null ? "Ожидание датчика" : data.optString("name","—"),x+24,y+78,21,505,white);
        float temp = value(data,"temperature"), load = value(data,"load");
        int thermal = Float.isFinite(temp) && temp >= (series==0?85:83) ? 0xFFFF7777 : color;
        text(c,number(temp),x+24,y+191,100,white,digits);
        p.setTypeface(digits); p.setTextSize(100);
        float unitX = x+24+p.measureText(number(temp))+8;
        text(c,"°C",unitX,y+146,26,thermal,digits);
        text(c,"ТЕМПЕРАТУРА",x+27,y+220,11,muted,medium);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFF253245);
        RectF arc = new RectF(x+348,y+112,x+500,y+264); c.drawArc(arc,140,260,false,p);
        if (Float.isFinite(load)) { p.setColor(color); c.drawArc(arc,140,260*Math.max(0,Math.min(1,load/100)),false,p); }
        p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        text(c,number(load),x+383,y+201,46,white,digits); text(c,"% LOAD",x+395,y+225,11,muted,medium);
        line(c,x+24,y+252,x+310,y+252,0xFF263446,1);
        stat(c,x+24,y+278,"ЧАСТОТА",number(value(data,"clock"))+" MHz");
        stat(c,x+193,y+278,"МОЩНОСТЬ",number(value(data,"power"))+" W");
        stat(c,x+367,y+293,"ВЕНТИЛЯТОР",number(value(data,"fan"))+" RPM");
    }
    private void stat(Canvas c,float x,float y,String title,String val) {
        text(c,title,x,y,10,muted,medium); text(c,val,x,y+26,21,white,regular);
    }
    private void memory(Canvas c) {
        box(c,36,469,344,221,0xFF101B29);
        text(c,"03   /   MEMORY",60,502,13,accent,medium);
        JSONObject ram = object("memory"); float used=value(ram,"used"), total=value(ram,"total"), load=value(ram,"load");
        text(c,decimal(used),60,564,52,white,digits);
        text(c,"/ "+number(total)+" GB",193,559,22,muted,digits);
        for (int i=0;i<24;i++) box(c,60+i*12,586,7,26,Float.isFinite(load) && i<load/100*24 ? accent:0xFF263446);
        text(c,"ИСПОЛЬЗОВАНО",60,651,11,muted,medium); text(c,number(load)+" %",271,651,20,white,regular);
    }
    private void graph(Canvas c) {
        box(c,398,469,766,221,0xFF101B29);
        text(c,"THERMAL HISTORY",422,502,13,white,medium);
        text(c,"CPU",865,502,11,accent,medium); text(c,"GPU",916,502,11,secondary,medium); text(c,"3 МИН",1065,502,11,muted,medium);
        float left=435, top=527, width=680, height=113;
        for(int i=0;i<3;i++) { float y=top+i*height/2; line(c,left,y,left+width,y,0xFF263446,1); text(c,""+(100-i*50),1126,y+4,10,muted,regular); }
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
        text(c,"−180 с",435,668,10,muted,regular); text(c,"−90 с",755,668,10,muted,regular); text(c,"СЕЙЧАС",1065,668,10,muted,regular);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_UP) {
            float x=(event.getX()-offsetX)/scale, y=(event.getY()-offsetY)/scale;
            if(settingsHit.contains(x,y)) { performClick(); settings.run(); }
            else if(themeHit.contains(x,y)) { performClick(); theme.run(); }
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
