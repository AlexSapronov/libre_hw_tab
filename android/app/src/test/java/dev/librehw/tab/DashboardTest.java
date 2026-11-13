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
        }
        render(view,"jade"); view.eva=true; render(view,"eva");
        data.getJSONObject("cpu").put("temperature",100).put("load",100);
        view.accept(data,"",true); render(view,"hot");
        view.accept(null,"ПК выключен",false); render(view,"offline");
        data.put("cpu",JSONObject.NULL).put("gpu",JSONObject.NULL).put("memory",JSONObject.NULL);
        view.accept(data,"",false); render(view,"missing");
        float scale=800f/760f, dx=(1340-1200*scale)/2;
        view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,dx+1080*scale,45*scale,0));
        view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,dx+900*scale,45*scale,0));
        assertEquals(1,clicks[0]); assertEquals(1,clicks[1]);
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
