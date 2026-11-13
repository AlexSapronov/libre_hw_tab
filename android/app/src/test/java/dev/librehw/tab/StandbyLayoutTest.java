package dev.librehw.tab;
import android.graphics.*;
import android.app.*;
import android.content.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StandbyLayoutTest {
    public static class OfflineActivity extends MainActivity { @Override protected boolean weatherEnabled(){return false;} }
    @Test public void StandbyWaitsForDisconnectAndReturnsWhenDataArrives() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});view.animations=false;view.layout(0,0,1340,800);
        view.accept(DemoData.sample(),"",false);assertFalse(view.standby());
        view.accept(null,"Нет сети",false);ShadowSystemClock.advanceBy(Duration.ofSeconds(16));assertTrue(view.standby());
        view.weather=new JSONObject().put("city","Санкт-Петербург").put("temperature",4).put("code",3).put("fetched",System.currentTimeMillis());view.nextAlarm="пн, 07:30";
        render(view,"standby-jade");view.themeId=6;render(view,"standby-nerv");
        view.accept(DemoData.sample(),"",false);assertFalse(view.standby());
    }
    @Test public void LayoutRoundTripRejectsOverlapsAndPreservesModesAndMetricChoice() {
        DashboardLayout layout=new DashboardLayout();assertTrue(layout.valid(false));assertTrue(layout.valid(true));
        layout.positions.put("overview.cpu",new RectF(36,125,300,400));layout.disabledMetrics.add("power");layout.hidden.add("overview.side");
        DashboardLayout copy=DashboardLayout.parse(layout.json());assertEquals(264,copy.rect("cpu",false).width(),.01);assertFalse(copy.metric("power"));assertFalse(copy.visible("side",false));
        assertEquals(890,copy.rect("main",true).width(),.01);
        layout.positions.put("overview.cpu",layout.rect("gpu",false));assertFalse(layout.valid(false));
        assertEquals(436,DashboardLayout.parse(layout.json()).rect("cpu",false).width(),.01);
    }
    @Test public void EditorDragResizeAndNervAreActuallyRendered() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});view.layout(0,0,1340,800);view.animations=false;
        view.accept(DemoData.sample(),"",true);view.themeId=6;view.mode=1;render(view,"nerv-overview");
        view.mode=2;render(view,"nerv-game");view.editing=true;render(view,"editor-nerv");
        view.layout.hidden.add("game.memory");view.layout.positions.put("game.main",new RectF(36,125,800,420));
        view.layout.positions.put("game.history",new RectF(36,443,926,690));render(view,"editor-custom");
        assertTrue(view.layout.valid(true));
    }
    @Test public void EditorTouchMovesResizesAndInvokesSave() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});view.layout(0,0,1340,800);view.animations=false;view.mode=1;view.editing=true;
        for(String key:new String[]{"gpu","memory","history","side"})view.layout.hidden.add("overview."+key);
        view.accept(DemoData.sample(),"",true);render(view,"editor-single");
        touch(view,0,100,200);touch(view,2,200,250);touch(view,1,200,250);
        assertEquals(136,view.layout.rect("cpu",false).left,.01);assertEquals(175,view.layout.rect("cpu",false).top,.01);
        render(view,"editor-moved");
        touch(view,0,564,517);touch(view,2,624,557);touch(view,1,624,557);
        assertEquals(496,view.layout.rect("cpu",false).width(),.01);assertEquals(390,view.layout.rect("cpu",false).height(),.01);
        int[] saved={0};view.onEditorSave=()->saved[0]++;touch(view,1,900,45);assertEquals(1,saved[0]);assertTrue(view.layout.valid(false));
    }
    private void touch(DashboardView view,int action,float x,float y) {
        float scale=800f/760f,dx=(1340-1200*scale)/2;
        view.onTouchEvent(android.view.MotionEvent.obtain(0,0,action,dx+x*scale,y*scale,0));
    }
    @Test public void AlarmUsesLocalSystemClockAndCorrectTime() throws Exception {
        try(org.robolectric.android.controller.ActivityController<OfflineActivity> controller=Robolectric.buildActivity(OfflineActivity.class).setup()) {
            MainActivity activity=controller.get();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("alarm");method.setAccessible(true);method.invoke(activity);
            TimePickerDialog dialog=(TimePickerDialog)ShadowDialog.getLatestDialog();dialog.updateTime(7,30);dialog.getButton(-1).performClick();
            Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(android.provider.AlarmClock.ACTION_SET_ALARM,intent.getAction());
            assertEquals(7,intent.getIntExtra(android.provider.AlarmClock.EXTRA_HOUR,-1));assertEquals(30,intent.getIntExtra(android.provider.AlarmClock.EXTRA_MINUTES,-1));
            assertFalse(intent.getBooleanExtra(android.provider.AlarmClock.EXTRA_SKIP_UI,true));
        }
    }
    @Test public void MagicPacketHasCorrectPrefixAndSixteenMacRepeats() {
        byte[] data=WakeClient.packet("00:11:22:33:44:55");assertEquals(102,data.length);
        for(int i=0;i<6;i++)assertEquals((byte)255,data[i]);
        for(int i=0;i<16;i++)for(int j=0;j<6;j++)assertEquals((byte)(j*0x11),data[6+i*6+j]);
        for(String invalid:new String[]{"invalid","ff:ff:ff:ff:ff:ff","00:00:00:00:00:00"}){try{WakeClient.packet(invalid);fail();}catch(IllegalArgumentException expected){}}
        assertEquals("Нет данных",WeatherClient.description(999));assertEquals("Снег",WeatherClient.description(73));
    }
    private void render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        File folder=new File("build/screenshots");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
}
