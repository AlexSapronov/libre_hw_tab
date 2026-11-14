package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.time.Duration;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaPanelTest {
    static JSONObject media() throws Exception {return new JSONObject("{id:'12345678901234567890123456789012',title:'Evangelion — soundtrack / playlist',artist:'YouTube · тестовые данные',playing:true,position:125,duration:3600,canNext:true,canPrevious:false,canToggle:true,canSeek:true,hasThumbnail:true}");}
    private DashboardView view() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});view.layout(0,0,1340,800);view.animations=false;view.themeId=6;
        for(int i=0;i<180;i++) {
            JSONObject sample=DemoData.sample();sample.getJSONObject("cpu").put("load",25+Math.sin(i/8.)*20);sample.getJSONObject("gpu").put("load",70+Math.sin(i/14.)*20);
            view.accept(sample,"",true);org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        }
        return view;
    }
    @Test public void MediaAppearsAndDisappearsWithoutChangingSavedLayoutAndButtonsHonorCapabilities() throws Exception {
        DashboardView view=view();String layout=view.layout.json();assertFalse(view.mediaVisible());
        JSONObject sample=DemoData.sample().put("media",media());view.accept(sample,"",true);
        view.mediaThumbnail=Bitmap.createBitmap(192,108,Bitmap.Config.ARGB_8888);
        Canvas poster=new Canvas(view.mediaThumbnail);poster.drawColor(0xFF1E1115);Paint paint=new Paint(3);paint.setColor(0xFFFF953D);paint.setTextSize(24);poster.drawText("NERV",15,45,paint);paint.setTextSize(12);poster.drawText("MEDIA TEST",15,75,paint);
        assertTrue(view.mediaVisible());String[] action={""};double[] fraction={0};view.onMedia=(a,f)->{action[0]=a;fraction[0]=f;};
        render(view,"nerv-media-overview");
        touch(view,900,645);assertEquals("",action[0]);touch(view,1100,645);assertEquals("next",action[0]);
        touch(view,1000,645);assertEquals("toggle",action[0]);touch(view,522,675);assertEquals("seek",action[0]);assertEquals(.5,fraction[0],.01);
        sample.getJSONObject("media").put("canSeek",false);view.accept(sample,"",true);touch(view,522,675);assertEquals(.5,fraction[0],.01);
        view.mode=2;render(view,"nerv-media-game");view.themeId=0;render(view,"jade-media-game");
        assertEquals(layout,view.layout.json());view.editing=true;assertFalse(view.mediaVisible());view.editing=false;
        view.accept(DemoData.sample(),"",true);assertFalse(view.mediaVisible());view.themeId=6;view.mode=1;render(view,"nerv-resource-overview");view.mode=2;render(view,"nerv-frame-time");
        view.accept(null,"offline",false);assertFalse(view.mediaVisible());
    }
    @Test public void TimelineFormatsHoursAndLongTitlesStayInsidePanel() throws Exception {
        assertEquals("2:05",DashboardView.mediaTime(125));assertEquals("1:00:00",DashboardView.mediaTime(3600));
        DashboardView view=view();JSONObject media=media().put("title","Очень длинное название видео ".repeat(15)).put("playing",false);
        view.accept(DemoData.sample().put("media",media),"",true);render(view,"nerv-media-paused");
    }
    private void touch(DashboardView view,float x,float y){float scale=800f/760f,dx=(1340-1200*scale)/2;view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,dx+x*scale,y*scale,0));}
    private void render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        File folder=new File("build/screenshots");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
}
