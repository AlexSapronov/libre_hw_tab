package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import java.io.*;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FocusDesignTest {
    private DashboardView view(int width,int height) throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
        view.layout(0,0,width,height);view.animations=false;view.themeId=8;view.mode=1;
        for(int i=0;i<60;i++){view.accept(DemoData.sample(),"",true);ShadowSystemClock.advanceBy(Duration.ofSeconds(1));}
        return view;
    }
    private void touch(DashboardView v,int action,float x,float y){v.onTouchEvent(MotionEvent.obtain(0,0,action,v.screenX(x),v.screenY(y),0));}
    private void tap(DashboardView v,float x,float y){touch(v,0,x,y);touch(v,1,x,y);}
    private void render(DashboardView v,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));
        File folder=new File("build/screenshots");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void tapExpandsLiveMetricAndReturnsWithoutChangingLayout() throws Exception {
        for(int width:new int[]{1340,2400}){
            DashboardView v=view(width,800);String saved=v.layout.json();render(v,"focus-before-"+width);
            tap(v,140,250);assertEquals("cpu",v.focusedMetric);render(v,"focus-cpu-"+width);
            v.accept(DemoData.sample().put("cpu",new JSONObject().put("temperature",92).put("name","CPU HOT")),"",true);
            render(v,"focus-hot-"+width);assertEquals("cpu",v.focusedMetric);
            tap(v,600,300);assertNull(v.focusedMetric);assertEquals(saved,v.layout.json());
            v.mode=2;v.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(v,"focus-game-"+width);
            tap(v,140,250);assertEquals("main",v.focusedMetric);assertFalse(v.mediaVisible());render(v,"focus-fps-"+width);
            assertTrue(v.closeFocus());assertTrue(v.mediaVisible());assertEquals(saved,v.layout.json());
            render(v,"focus-side-"+width);tap(v,1040,220);assertEquals("gpu",v.focusedMetric);v.closeFocus();
            v.editing=true;render(v,"focus-editor-"+width);tap(v,140,250);assertNull(v.focusedMetric);
        }
    }
    @Test public void cancelledAndDraggedGesturesDoNotExpandAndStandbyClearsFocus() throws Exception {
        DashboardView v=view(1340,800);render(v,"focus-gestures");
        touch(v,0,140,250);touch(v,3,140,250);assertNull(v.focusedMetric);
        touch(v,0,140,250);touch(v,2,200,250);touch(v,1,200,250);assertNull(v.focusedMetric);
        tap(v,140,250);assertEquals("cpu",v.focusedMetric);
        v.forceStandby=true;render(v,"focus-standby");assertNull(v.focusedMetric);
    }
    @Test public void themesDetailLevelsAndStatusRemainReadable() throws Exception {
        DashboardView v=view(1920,1080);
        for(int theme=6;theme<DashboardTheme.ALL.length;theme++)for(int detail=0;detail<3;detail++){
            v.themeId=theme;v.detailLevel=detail;v.mode=1;render(v,"design-"+DashboardTheme.ALL[theme].name+"-"+detail);
            v.mode=2;v.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(v,"design-media-"+theme+"-"+detail);
            v.clear();v.accept(DemoData.sample(),"",true);
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5));assertTrue(v.statusText().contains("5 С НАЗАД"));
        v.accept(null,"Нет соединения",false);assertTrue(v.statusText().contains("SIGNAL LOST"));render(v,"design-signal-lost");
    }
}
