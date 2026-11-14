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
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResponsiveCardsTest {
    private DashboardView view() throws Exception {
        DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});view.layout(0,0,1340,800);view.animations=false;view.themeId=6;
        for(int i=0;i<181;i++){JSONObject data=DemoData.sample();data.getJSONObject("game").put("fps",120+Math.sin(i/12.)*35);view.accept(data,"",true);ShadowSystemClock.advanceBy(Duration.ofSeconds(1));}
        return view;
    }
    @Test public void WideCardsReflowContentToLeadingEdgeInsteadOfCenteringOldSizedCard() throws Exception {
        DashboardView view=view();
        for(String key:new String[]{"cpu","gpu","memory","history","side","main"}) {
            boolean game=key.equals("main");view.mode=game?2:1;view.layout=new DashboardLayout();
            for(String other:DashboardLayout.keys(game))if(!other.equals(key))view.layout.hidden.add(DashboardLayout.id(other,game));
            view.layout.positions.put(DashboardLayout.id(key,game),new RectF(36,125,736,375));
            Bitmap bitmap=render(view,"reflow-wide-"+key);
            float scale=800f/760f,dx=(1340-1200*scale)/2;int content=0;
            for(int y=(int)(145*scale);y<195*scale;y++)for(int x=(int)(dx+54*scale);x<dx+110*scale;x++) {
                int color=bitmap.getPixel(x,y);if(Color.red(color)>160 && Color.green(color)>60)content++;
            }
            assertTrue(key+" must place content near its actual leading padding",content>20);bitmap.recycle();
        }
    }
    @Test public void CompactTallWideAndMediaLayoutsRenderForEveryCardWithoutMutatingSavedLayout() throws Exception {
        DashboardView view=view();
        float[][] sizes={{160,110},{220,450},{500,180}};
        for(String key:new String[]{"cpu","memory","history","side","main"})for(int n=0;n<sizes.length;n++) {
            boolean game=key.equals("main") || key.equals("side");view.mode=game?2:1;view.layout=new DashboardLayout();
            for(String other:DashboardLayout.keys(game))if(!other.equals(key))view.layout.hidden.add(DashboardLayout.id(other,game));
            view.layout.positions.put(DashboardLayout.id(key,game),new RectF(36,125,36+sizes[n][0],125+sizes[n][1]));
            assertTrue(view.layout.valid(game));String saved=view.layout.json();
            render(view,"reflow-"+key+"-"+n).recycle();
            view.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);
            render(view,"reflow-media-"+key+"-"+n).recycle();assertEquals(saved,view.layout.json());view.accept(DemoData.sample(),"",true);
        }
        view.layout=new DashboardLayout();view.mode=2;render(view,"reflow-game-full").recycle();
        view.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(view,"reflow-game-media").recycle();
        view.mode=1;render(view,"reflow-overview-media").recycle();view.accept(DemoData.sample(),"",true);render(view,"reflow-overview-full").recycle();
    }
    private Bitmap render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        File folder=new File("build/screenshots");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}return bitmap;
    }
}
