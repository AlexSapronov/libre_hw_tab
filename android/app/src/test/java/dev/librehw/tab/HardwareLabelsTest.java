package dev.librehw.tab;
import android.graphics.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HardwareLabelsTest {
    @After public void reset(){UiText.language("ru");}
    @Test public void hardwareKeepsBothLabelsAcrossMediaSizesAndLanguages()throws Exception{
        for(String language:new String[]{"ru","en"})for(boolean media:new boolean[]{false,true})for(int height:new int[]{600,800}){
            UiText.language(language);DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});
            v.layout(0,0,height==600?1024:1340,height);v.themeId=8;v.detailLevel=2;v.mode=2;v.animations=false;v.autoStandby=false;
            JSONObject data=DemoData.sample();if(media)data.put("media",MediaPanelTest.media());v.accept(data,"",true);
            Bitmap b=Bitmap.createBitmap(v.getWidth(),height,Bitmap.Config.ARGB_8888);EditorHeaderTest.RecordingCanvas c=new EditorHeaderTest.RecordingCanvas(b);v.draw(c);
            assertEquals(2,Collections.frequency(c.labels,UiText.get("ТЕМПЕРАТУРА")));assertEquals(2,Collections.frequency(c.labels,UiText.get("ЗАГРУЗКА")));
            assertFalse(c.labels.stream().anyMatch(t->t.matches("[0-9]+°C / [0-9]+%")));
            File dir=new File("build/screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"hardware-labels-"+language+"-"+media+"-"+height+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        }
    }
}
