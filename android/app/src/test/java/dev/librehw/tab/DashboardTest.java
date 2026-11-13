package dev.librehw.tab;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.json.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardTest {
    @Test public void rendersThemesMissingSensorsAndTouchControls() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        int[] clicks = {0,0};
        DashboardView view = new DashboardView(context, () -> clicks[0]++, () -> clicks[1]++);
        view.layout(0,0,1340,800);
        JSONObject data = DemoData.sample();
        for(int i=0;i<180;i++) {
            data.getJSONObject("cpu").put("temperature",58+Math.sin(i/15.0)*8);
            data.getJSONObject("gpu").put("temperature",65+Math.sin(i/19.0)*6);
            view.accept(data,"",true);
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        }
        render(view,"jade"); view.themeId=1; render(view,"eva");
        for (int load : new int[] {1,12,100}) {
            data.getJSONObject("cpu").put("load",load);
            data.getJSONObject("gpu").put("load",load);
            view.accept(data,"",true);
            assertLoadCentered(view);
            render(view,"load-"+load);
        }
        data.getJSONObject("cpu").put("temperature",100).put("load",100);
        view.accept(data,"",true); render(view,"hot");
        data.getJSONObject("cpu").put("temperature",58).put("load",42);
        data.getJSONObject("gpu").put("temperature",65).put("load",94);
        data.getJSONObject("game").put("active",true);
        view.accept(data,"",true); render(view,"game-eva");
        view.themeId=0; render(view,"game-jade");
        for(int theme=0;theme<DashboardTheme.ALL.length;theme++) {
            view.themeId=theme;view.mode=0;render(view,"theme-"+DashboardTheme.ALL[theme].name.toLowerCase()+"-game");
            view.mode=1;render(view,"theme-"+DashboardTheme.ALL[theme].name.toLowerCase()+"-overview");
        }
        view.themeId=0;view.mode=0;
        data.getJSONObject("game").put("paused",true).put("fps",org.json.JSONObject.NULL).put("frames",new org.json.JSONArray()).put("frameSeries",new org.json.JSONArray());
        view.accept(data,"",true);render(view,"game-paused");
        view.mode=1;render(view,"game-overview-override");view.mode=0;
        view.accept(null,"ПК выключен",false); render(view,"offline");
        data.put("cpu",JSONObject.NULL).put("gpu",JSONObject.NULL).put("memory",JSONObject.NULL);
        view.accept(data,"",false); render(view,"missing");
        float scale=800f/760f, dx=(1340-1200*scale)/2;
        view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,dx+1080*scale,45*scale,0));
        view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,dx+900*scale,45*scale,0));
        assertEquals(1,clicks[0]); assertEquals(1,clicks[1]);
    }
    private void assertLoadCentered(DashboardView view) {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        float scale=800f/760f, dx=(1340-1200*scale)/2;
        for (float cardX : new float[] {36,490}) {
            float center=dx+(cardX+342)*scale;
            int min=1340,max=0;
            for (int y=(int)((125+148)*scale);y<(125+205)*scale;y++) {
                for (int x=(int)(center-65*scale);x<center+65*scale;x++) {
                    int color=bitmap.getPixel(x,y);
                    if(Color.red(color)>210 && Color.green(color)>210 && Color.blue(color)>210) { min=Math.min(min,x); max=Math.max(max,x); }
                }
            }
            assertTrue("Numeral must be present",max>min);
            assertEquals("Load numeral must be centered in its gauge",center,(min+max)/2f,5*scale);
        }
        bitmap.recycle();
    }
    private void render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        assertNotEquals(Color.TRANSPARENT,bitmap.getPixel(50,50));
        File folder=new File("build/screenshots"); folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        bitmap.recycle();
    }
}
