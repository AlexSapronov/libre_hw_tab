package dev.librehw.tab;
import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AtmosphereTest {
    @After public void reset(){UiText.language("ru");RuntimeEnvironment.getApplication().getSharedPreferences("connection",0).edit().clear().commit();}
    @Test public void noticesAreEdgesAndMissingSamplesDoNotEndGamesOrClearThermalState()throws Exception{
        AtmosphereState s=new AtmosphereState();JSONObject d=DemoData.sample();d.getJSONObject("cpu").put("temperature",60);d.getJSONObject("gpu").put("temperature",65);d.getJSONObject("game").put("active",false).put("paused",false);
        s.accept(d,0);d.getJSONObject("game").put("active",true);assertEquals("confirm",s.accept(d,1));assertEquals("COMBAT TELEMETRY ENGAGED",s.notice);
        assertEquals("",s.accept(d,2));JSONObject withoutGame=new JSONObject(d.toString());withoutGame.remove("game");s.accept(withoutGame,2);assertTrue(s.game);s.accept(null,3);assertTrue(s.game);s.accept(d,4000);assertFalse(s.notice.equals("OPERATION COMPLETE"));
        d.getJSONObject("cpu").put("temperature",86);assertEquals("alert",s.accept(d,8000));assertEquals("CPU / THERMAL LIMIT",s.notice);assertEquals("",s.accept(d,9000));
        d.getJSONObject("cpu").put("temperature",84);s.accept(d,10000);assertTrue(s.cpuHot);d.getJSONObject("cpu").remove("temperature");s.accept(d,11000);assertTrue(s.cpuHot);
        d.getJSONObject("cpu").put("temperature",82);s.accept(d,12000);assertFalse(s.cpuHot);assertEquals("CPU / CONDITION NORMAL",s.notice);
        d.getJSONObject("game").put("active",false).put("paused",true);s.accept(d,16000);assertTrue(s.game);
        d.getJSONObject("game").put("paused",false);s.accept(d,20000);assertEquals("OPERATION COMPLETE",s.notice);assertEquals("",s.accept(d,21000));
    }
    DashboardView view()throws Exception{DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});v.layout(0,0,1340,800);v.themeId=8;v.detailLevel=2;v.mode=1;v.animations=false;v.autoStandby=false;v.accept(DemoData.sample(),"",false);return v;}
    @Test public void magiLongPressShowsRealStateAndReleaseDoesNotImmediatelyDismiss()throws Exception{
        DashboardView v=view();
        var controller=Robolectric.buildActivity(android.app.Activity.class).setup();controller.get().setContentView(v);v.layout(0,0,1340,800);
        render(v,"atmosphere-overview");String saved=v.layout.json();
        touch(v,MotionEvent.ACTION_DOWN,90,45);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+1));assertTrue(v.magiOpen);
        touch(v,MotionEvent.ACTION_UP,90,45);assertTrue(v.magiOpen);render(v,"atmosphere-magi-live");assertTrue(v.closeFocus());assertFalse(v.magiOpen);assertEquals(saved,v.layout.json());
        touch(v,0,90,45);touch(v,2,500,400);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));touch(v,1,500,400);assertFalse(v.magiOpen);
        v.accept(null,"offline",false);v.magiOpen=true;render(v,"atmosphere-magi-offline");assertTrue(v.closeFocus());
        v.editing=true;touch(v,0,90,45);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));touch(v,1,90,45);assertFalse(v.magiOpen);controller.pause().stop().destroy();
    }
    @Test public void renderThemeCharactersEventsStandbyAndTransitions()throws Exception{
        DashboardView v=view();JSONObject d=DemoData.sample();d.getJSONObject("cpu").put("temperature",60);d.getJSONObject("gpu").put("temperature",65);d.getJSONObject("game").put("active",false).put("paused",false);v.accept(d,"",false);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
        for(int theme:new int[]{7,8,9}){v.themeId=theme;v.animations=false;render(v,"atmosphere-unit-"+theme);v.forceStandby=true;render(v,"atmosphere-standby-"+theme);v.forceStandby=false;}
        v.themeId=8;v.animations=true;v.mode=0;render(v,"atmosphere-before-combat");d.getJSONObject("game").put("active",true);v.accept(d,"",false);render(v,"atmosphere-combat-start");ShadowSystemClock.advanceBy(Duration.ofMillis(220));render(v,"atmosphere-combat-transition");ShadowSystemClock.advanceBy(Duration.ofMillis(600));render(v,"atmosphere-combat-ready");
        d.getJSONObject("gpu").put("temperature",90);v.accept(d,"",false);render(v,"atmosphere-thermal");d.getJSONObject("gpu").put("temperature",70);v.accept(d,"",false);render(v,"atmosphere-normal");
        UiText.language("en");v.animations=false;v.forceStandby=true;render(v,"atmosphere-standby-en");v.magiOpen=true;render(v,"atmosphere-magi-en");
    }
    @Test public void themeMenuSortPreservesPersistedIdsAndSoundsDefaultOff()throws Exception{
        var preferences=RuntimeEnvironment.getApplication().getSharedPreferences("connection",0);preferences.edit().putInt("theme_id",8).commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=controller.get();
        java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("toggleTheme");method.setAccessible(true);method.invoke(a);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.getListView().getAdapter().getItem(0).toString().startsWith("EVA-00"));assertEquals(1,dialog.getListView().getCheckedItemPosition());
        dialog.getListView().performItemClick(null,2,2);assertEquals(9,preferences.getInt("theme_id",-1));assertEquals("EVA-02",DashboardTheme.ALL[9].name);
        assertFalse(preferences.getBoolean("interface_sound",false));HashSet<Integer> ids=new HashSet<>();for(int id:DashboardTheme.MENU_ORDER)ids.add(id);assertEquals(10,ids.size());
        assertTrue(DashboardTheme.ALL[7].transitionMillis()>DashboardTheme.ALL[9].transitionMillis());controller.destroy();
    }
    void touch(DashboardView v,int action,float x,float y){v.onTouchEvent(MotionEvent.obtain(0,0,action,v.screenX(x),v.screenY(y),0));}
    void render(DashboardView v,String name)throws Exception{Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
