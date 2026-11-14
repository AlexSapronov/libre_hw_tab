package dev.librehw.tab;
import android.graphics.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MagiStyleTest {
    private DashboardView view() throws Exception {
        DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
        v.layout(0,0,1340,800);v.themeId=6;v.animations=false;v.autoStandby=false;v.mode=1;
        JSONObject data=DemoData.sample();data.getJSONObject("cpu").put("temperature",60);data.getJSONObject("gpu").put("temperature",65);
        for(int i=0;i<60;i++){v.accept(data,"",false);org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));}return v;
    }
    private Bitmap render(DashboardView v,String name)throws Exception{
        Bitmap b=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File f=new File("build/screenshots");f.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(f,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}return b;
    }
    @Test public void fullMagiHasDistinctOpaqueHeaderAndStatusEvenWithoutAnimation()throws Exception{
        DashboardView v=view();String saved=v.layout.json();
        v.detailLevel=1;Bitmap quiet=render(v,"compare-nerv");
        v.detailLevel=2;Bitmap full=render(v,"compare-magi");
        int x=(int)v.screenX(450),y=(int)v.screenY(151);
        assertNotEquals(quiet.getPixel(x,y),full.getPixel(x,y));assertEquals(DashboardTheme.ALL[6].primary,full.getPixel(x,y));
        assertEquals("LIVE",v.operationalState());assertEquals(saved,v.layout.json());quiet.recycle();full.recycle();
        v.mode=2;v.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(v,"magi-game-media").recycle();
        v.editing=true;render(v,"magi-editor").recycle();v.editing=false;
        v.themeId=8;render(v,"magi-eva01").recycle();
        v.forceStandby=true;render(v,"magi-standby").recycle();
    }
    @Test public void statusReflectsRealDataAndThermalHeaderIsScopedToAffectedDevice()throws Exception{
        DashboardView v=view();v.detailLevel=2;JSONObject data=DemoData.sample();
        data.getJSONObject("cpu").put("temperature",92);data.getJSONObject("gpu").put("temperature",65);
        v.accept(data,"",false);assertEquals("THERMAL ALERT",v.operationalState());Bitmap hot=render(v,"magi-thermal-alert");
        assertEquals(DashboardView.ALERT,hot.getPixel((int)v.screenX(450),(int)v.screenY(151)));
        assertEquals(DashboardTheme.ALL[6].primary,hot.getPixel((int)v.screenX(904),(int)v.screenY(151)));hot.recycle();
        data.getJSONObject("cpu").put("temperature",60);v.accept(data,"",true);assertEquals("DEMO",v.operationalState());
        data.put("sensorError","Test error");v.accept(data,"",false);assertEquals("SENSOR ERROR",v.operationalState());render(v,"magi-sensor-error").recycle();
        v.accept(null,"Нет соединения",false);assertEquals("SIGNAL LOST",v.operationalState());render(v,"magi-signal-lost").recycle();
    }
}
