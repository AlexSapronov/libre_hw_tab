package dev.librehw.tab;
import android.graphics.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import java.io.*;
import java.time.Duration;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BootDemoRenderTest {
    @Test public void renderExactNativeBootFrames()throws Exception{
        UiText.language("ru");DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
        v.layout(0,0,1340,800);v.themeId=8;v.detailLevel=2;v.mode=1;v.animations=true;v.autoStandby=false;v.forceStandby=true;
        File dir=new File("build/demo-frames");dir.mkdirs();long previous=0;
        JSONObject data=DemoData.sample();
        for(int frame=0;frame<288;frame++){
            long elapsed=frame*1000L/24;ShadowSystemClock.advanceBy(Duration.ofMillis(elapsed-previous));previous=elapsed;
            if(frame==24)v.beginWake();
            if(frame>=168 && frame%12==0)v.accept(data,"",false);
            Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));
            try(FileOutputStream out=new FileOutputStream(new File(dir,String.format(java.util.Locale.ROOT,"frame-%04d.png",frame)))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        }
    }
}
