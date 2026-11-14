package dev.librehw.tab;

import android.graphics.*;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FullscreenTest {
    @Test public void fillsTabletAndWideDisplaysAndKeepsTouchesAligned() throws Exception {
        int[][] sizes={{1340,800},{1920,1200},{1920,1080},{2400,1080},{1024,768}};
        for(int[] size:sizes){
            int[] clicks={0};
            DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->clicks[0]++,()->{});
            view.layout(0,0,size[0],size[1]);view.animations=false;view.themeId=6;view.mode=1;
            view.accept(DemoData.sample(),"",true);
            String saved=view.layout.json();
            Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            // Cards must reach the outer 2% on BOTH sides, not sit in a centered letterbox.
            assertEquals(DashboardTheme.ALL[6].panel,bitmap.getPixel(Math.round(size[0]*.02f),Math.round(size[1]*.25f)));
            assertEquals(DashboardTheme.ALL[6].panel,bitmap.getPixel(Math.round(size[0]*.98f),Math.round(size[1]*.25f)));
            // Independent physical coordinates for the connection button's design center.
            float x=(1080-24)*size[0]/1152f,y=45*size[1]/760f;
            view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0));
            view.onTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0));
            assertEquals(1,clicks[0]);assertEquals(saved,view.layout.json());
            File folder=new File("build/screenshots");folder.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(folder,"fullscreen-"+size[0]+"x"+size[1]+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            bitmap.recycle();
        }
    }
}
