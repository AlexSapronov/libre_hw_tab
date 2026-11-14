package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CoolingFocusTest {
    @Test public void calibrationHonorsFloorPlateausAndUnknowns(){
        assertEquals(30,FanCalibration.percent("gpuFan",1150),0);
        assertEquals(30,FanCalibration.percent("gpuFan",1149),0);
        assertEquals(0,FanCalibration.percent("gpuFan",0),0);
        assertEquals(100,FanCalibration.percent("gpuFan",4000),0);
        assertEquals(45,FanCalibration.percent("gpuFan",1678),.01);
        assertEquals(55,FanCalibration.percent("fan",1249),0);
        assertEquals(45,FanCalibration.percent("sideFan",1080),0);
        assertEquals(65,FanCalibration.percent("sideFan",1368),0);
        assertEquals(30,FanCalibration.percent("fan",676),0);
        assertTrue(Float.isNaN(FanCalibration.percent("pump",2700)));
        assertTrue(Float.isNaN(FanCalibration.percent("fan",Float.NaN)));
    }
    @Test public void bothBlocksOpenAndReturnWithoutChangingLayout() throws Exception {
        DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
        v.layout(0,0,1340,800);v.themeId=8;v.detailLevel=2;v.mode=1;v.animations=false;v.autoStandby=false;
        for(int i=0;i<60;i++){v.accept(DemoData.sample(),"",true);org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));}
        String layout=v.layout.json();render(v,"calibrated-overview");
        for(String key:new String[]{"side","network"}){
            RectF r=v.layout.rect(key,false);tap(v,r.centerX(),r.centerY());assertEquals(key,v.focusedMetric);
            render(v,"focus-"+key);tap(v,600,400);assertNull(v.focusedMetric);assertEquals(layout,v.layout.json());render(v,"calibrated-return");
            tap(v,r.centerX(),r.centerY());assertTrue(v.closeFocus());render(v,"calibrated-back");
        }
        v.editing=true;render(v,"calibrated-editor");RectF r=v.layout.rect("side",false);tap(v,r.centerX(),r.centerY());assertNull(v.focusedMetric);
    }
    private void tap(DashboardView v,float x,float y){v.onTouchEvent(MotionEvent.obtain(0,0,0,v.screenX(x),v.screenY(y),0));v.onTouchEvent(MotionEvent.obtain(0,1,1,v.screenX(x),v.screenY(y),0));}
    private void render(DashboardView v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
