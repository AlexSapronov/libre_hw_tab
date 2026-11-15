package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EvaBootTest {
    @After public void reset(){UiText.language("ru");}
    DashboardView view(){DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});v.layout(0,0,1340,800);v.themeId=8;v.detailLevel=2;v.mode=1;v.animations=true;v.autoStandby=false;return v;}
    void advance(long ms){ShadowSystemClock.advanceBy(Duration.ofMillis(ms));}
    @Test public void startupOccupiesScreenAndRevealsRealDashboardOnlyAfterLink()throws Exception{
        DashboardView v=view();assertTrue(v.beginWake());render(v,"eva-boot-01-power");
        advance(1800);render(v,"eva-boot-02-magi");advance(3000);render(v,"eva-boot-03-acquiring");
        assertTrue(v.wakePending());assertEquals(-1,v.boot.linked);
        int[] menu={0};v.onMenu=()->menu[0]++;tap(v,360,45);assertEquals(0,menu[0]);
        String layout=v.layout.json();v.accept(DemoData.sample(),"",false);render(v,"eva-boot-04-linked");assertTrue(v.boot.active);
        advance(1200+400);assertTrue(v.boot.revealing(android.os.SystemClock.elapsedRealtime(),true));render(v,"eva-boot-05-reveal");
        advance(800);render(v,"eva-boot-06-dashboard");assertFalse(v.boot.active);assertEquals(layout,v.layout.json());assertFalse(v.canWake());
    }
    @Test public void quickLinkStillPlaysIntroAndLostLinkResumesRealWait()throws Exception{
        DashboardView v=view();v.beginWake();v.accept(DemoData.sample(),"",false);advance(200);render(v,"eva-boot-fast-link");assertTrue(v.boot.active);assertFalse(v.boot.revealing(android.os.SystemClock.elapsedRealtime(),true));
        v.accept(null,"offline",false);assertTrue(v.wakePending());assertEquals(-1,v.boot.linked);
        advance(60000);render(v,"eva-boot-07-error");assertFalse(v.wakePending());assertFalse(v.wakeError.isEmpty());
        v.onWake=()->v.beginWake();tapBoot(v,200,670);assertTrue(v.wakePending());assertTrue(v.wakeError.isEmpty());
        v.failWake("Wake send failed");tapBoot(v,800,670);assertFalse(v.boot.active);assertTrue(v.forceStandby);
    }
    @Test public void reducedMotionAndEnglishStillShowFullScreenState()throws Exception{
        UiText.language("en");DashboardView v=view();v.themeId=9;v.animations=false;v.beginWake();advance(5000);
        render(v,"eva-boot-static-en");assertTrue(v.boot.active);v.accept(DemoData.sample(),"",false);render(v,"eva-boot-static-complete");assertFalse(v.boot.active);
        v.accept(null,"offline",false);v.beginWake();advance(60000);render(v,"eva-boot-error-en");
    }
    void tap(DashboardView v,float x,float y){v.onTouchEvent(MotionEvent.obtain(0,0,0,v.screenX(x),v.screenY(y),0));v.onTouchEvent(MotionEvent.obtain(0,1,1,v.screenX(x),v.screenY(y),0));}
    void tapBoot(DashboardView v,float x,float y){v.onTouchEvent(MotionEvent.obtain(0,0,0,x*v.getWidth()/1200,y*v.getHeight()/760,0));v.onTouchEvent(MotionEvent.obtain(0,1,1,x*v.getWidth()/1200,y*v.getHeight()/760,0));}
    void render(DashboardView v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
