package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import java.io.*;
import java.time.Duration;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WakeFeedbackTest {
    DashboardView view(){DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});v.layout(0,0,1340,800);v.themeId=8;v.detailLevel=2;v.forceStandby=true;return v;}
    @Test public void waitingEndsAtConnectionOrExactlyOneMinute()throws Exception{
        DashboardView v=view();assertTrue(v.beginWake());assertFalse(v.beginWake());render(v,"wake-start");
        ShadowSystemClock.advanceBy(Duration.ofSeconds(59));v.updateWake();assertTrue(v.wakePending());render(v,"wake-pending");
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));v.updateWake();assertFalse(v.wakePending());assertFalse(v.wakeError.isEmpty());render(v,"wake-timeout");
        assertTrue(v.beginWake());assertTrue(v.wakeError.isEmpty());v.accept(DemoData.sample(),"",false);assertFalse(v.wakePending());assertFalse(v.forceStandby);assertFalse(v.canWake());
        v.forceStandby=true;render(v,"wake-connected-hidden");int[] clicks={0};v.onWake=()->clicks[0]++;tap(v);assertEquals(0,clicks[0]);
        v.accept(null,"",false);assertTrue(v.canWake());assertTrue(v.beginWake());v.failWake("Send failed");assertEquals("Send failed",v.wakeError);assertTrue(v.canWake());
    }
    @Test public void pendingButtonCannotSendAgainAndStaticModeStillTimesOut()throws Exception{
        DashboardView v=view();int[] clicks={0};v.onWake=()->{clicks[0]++;v.beginWake();};render(v,"wake-idle");tap(v);assertEquals(1,clicks[0]);tap(v);assertEquals(1,clicks[0]);
        v.animations=false;render(v,"wake-static");ShadowSystemClock.advanceBy(Duration.ofSeconds(60));render(v,"wake-static-timeout");assertFalse(v.wakePending());
    }
    void tap(DashboardView v){v.onTouchEvent(MotionEvent.obtain(0,0,0,v.screenX(300),v.screenY(620),0));v.onTouchEvent(MotionEvent.obtain(0,1,1,v.screenX(300),v.screenY(620),0));}
    void render(DashboardView v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
