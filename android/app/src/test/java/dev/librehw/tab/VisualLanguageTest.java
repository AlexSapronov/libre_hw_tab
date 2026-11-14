package dev.librehw.tab;

import android.app.AlertDialog;
import android.graphics.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisualLanguageTest {
    @After public void resetLanguage(){UiText.language("ru");RuntimeEnvironment.getApplication().getSharedPreferences("connection",0).edit().clear().commit();}
    @Test public void realHistoryPreservesZeroGapsBoundsAndSourceChanges() throws Exception {
        MetricHistory history=new MetricHistory();JSONObject data=DemoData.sample();
        data.getJSONObject("cpu").put("fan",0);history.accept(data,0);
        assertEquals(0,history.points("cpu.fan").iterator().next().value,0);
        history.accept(null,500);assertTrue(Float.isNaN(last(history,"cpu.fan").value));
        data.getJSONObject("cpu").put("fan",1234);history.accept(data,1000);assertTrue(history.maximum("cpu.fan")>=1234);
        data.getJSONObject("network").put("status","warming");history.accept(data,1500);assertTrue(Float.isNaN(last(history,"network.downloadBps").value));
        data.getJSONObject("network").put("name","New adapter").put("status","ready");history.accept(data,2000);assertEquals(1,size(history,"network.downloadBps"));
        data.getJSONObject("game").put("sourceId","new-game");history.accept(data,2500);assertEquals(1,size(history,"game.fps"));
        for(int i=0;i<300;i++)history.accept(data,3000+i*500);
        assertTrue(size(history,"cpu.fan")<=121);
        history.clear();assertEquals(0,size(history,"cpu.fan"));assertEquals(1,history.maximum("cpu.fan"),0);
    }
    private int size(MetricHistory h,String key){int count=0;for(MetricHistory.Point p:h.points(key))count++;return count;}
    private MetricHistory.Point last(MetricHistory h,String key){MetricHistory.Point last=null;for(MetricHistory.Point p:h.points(key))last=p;return last;}
    private DashboardView view() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
        view.layout(0,0,1340,800);view.themeId=8;view.detailLevel=2;view.animations=false;view.autoStandby=false;view.mode=1;
        JSONObject data=DemoData.sample();
        for(int i=0;i<65;i++){
            double wave=Math.sin(i*.18);
            data.getJSONObject("cpu").put("temperature",59+wave*5).put("fan",900+wave*200).put("sideFan",1300-wave*300).put("pump",2700+wave*100).put("clock",4200+wave*400).put("power",65+wave*12);
            data.getJSONObject("gpu").put("temperature",65+wave*7).put("fan",1500+wave*350);
            data.getJSONObject("network").put("downloadBps",(wave+1)*15000000).put("uploadBps",(Math.cos(i*.28)+1)*2000000);
            data.getJSONObject("game").put("fps",140+wave*20).put("low1",100+wave*10).put("low01",60+wave*8).put("frameTime",7+wave);
            view.accept(data,"",true);ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        }return view;
    }
    private List<String> render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        EditorHeaderTest.RecordingCanvas canvas=new EditorHeaderTest.RecordingCanvas(bitmap);view.draw(canvas);
        File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();return canvas.labels;
    }
    @Test public void englishCoversOverviewGameEditorFocusAndOfflineStandby() throws Exception {
        UiText.language("en");DashboardView view=view();String layout=view.layout.json();
        assertEnglish(render(view,"visuals-en-overview"));
        view.mode=2;assertEnglish(render(view,"visuals-en-game"));
        view.editing=true;assertEnglish(render(view,"visuals-en-editor"));view.editing=false;
        view.focusedMetric="cpu";assertEnglish(render(view,"visuals-en-focus"));view.focusedMetric=null;
        view.forceStandby=true;
        // A cached weather response created before changing language must also translate offline.
        view.weather=new JSONObject().put("city","London").put("temperature",7).put("code",61).put("summary","Дождь");
        JSONArray forecast=WeatherClient.upcoming(view.weather,java.time.Instant.now());
        for(int i=0;i<forecast.length();i++)forecast.getJSONObject(i).put("temperature",7+i*2).put("code",61).put("probability",60).put("precipitation",1.2);
        view.weather.put("forecast",forecast);
        assertEnglish(render(view,"visuals-en-standby"));view.forceStandby=false;
        view.mode=1;view.accept(null,UiText.get("Нет связи с ПК"),false);assertEnglish(render(view,"visuals-en-disconnected"));
        assertEquals(layout,view.layout.json());
        UiText.language("ru");view=view();render(view,"visuals-ru-overview");view.themeId=0;render(view,"visuals-jade");
        view.layout(0,0,1024,600);view.mode=2;view.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(view,"visuals-compact-media");view.themeId=8;UiText.language("en");render(view,"visuals-compact-media-magi");
        view.layout(0,0,1920,1200);view.mode=1;render(view,"visuals-overview-media-16x10");
    }
    private void assertEnglish(List<String> labels){for(String label:labels)assertFalse("Untranslated: "+label,label.matches(".*[А-Яа-яЁё].*"));}
    @Test public void languageSelectionPersistsAndChangesNativeDialogs() throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        java.lang.reflect.Method language=MainActivity.class.getDeclaredMethod("language");language.setAccessible(true);language.invoke(activity);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,1,1);
        assertEquals("en",activity.getSharedPreferences("connection",0).getString("language",""));controller.destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create();activity=controller.get();assertEquals("en",UiText.language());
        java.lang.reflect.Method menu=MainActivity.class.getDeclaredMethod("menu");menu.setAccessible(true);menu.invoke(activity);
        dialog=ShadowAlertDialog.getLatestAlertDialog();assertEquals("Standby screen",dialog.getListView().getAdapter().getItem(0));
        assertEquals("Layout editor",dialog.getListView().getAdapter().getItem(1));
        assertEquals("Full MAGI",DashboardView.detailLabels()[2]);
        for(DashboardTheme theme:DashboardTheme.ALL)assertFalse(UiText.get(theme.description).matches(".*[А-Яа-яЁё].*"));
        assertEquals("Rain",WeatherClient.description(61));assertEquals("TODAY",WeatherClient.dayLabel("2026-11-14",java.time.LocalDate.of(2026,11,14)));
        controller.destroy();
    }
}
