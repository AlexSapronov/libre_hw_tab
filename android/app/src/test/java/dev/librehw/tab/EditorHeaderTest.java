package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorHeaderTest {
    static class RecordingCanvas extends Canvas {
        final List<String> labels=new ArrayList<>();
        RecordingCanvas(Bitmap bitmap){super(bitmap);}
        @Override public void drawText(String text,float x,float y,Paint paint){labels.add(text);super.drawText(text,x,y,paint);}
    }
    @Test public void editorHeaderReplacesNormalHeaderAndControlsStillWork() throws Exception {
        for(int theme:new int[]{0,6,9})for(int mode:new int[]{1,2}){
            int[] clicks={0,0,0,0};
            DashboardView view=new DashboardView(RuntimeEnvironment.getApplication(),()->clicks[3]++,()->clicks[3]++);
            view.layout(0,0,1340,800);view.themeId=theme;view.mode=mode;view.animations=false;
            view.accept(DemoData.sample(),"",true);
            Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap)); // enter editor from an already drawn dashboard
            view.editing=true;view.onEditorOptions=()->clicks[0]++;view.onEditorSave=()->clicks[1]++;view.onEditorCancel=()->clicks[2]++;
            RecordingCanvas canvas=new RecordingCanvas(bitmap);view.draw(canvas);
            for(String label:new String[]{"РЕДАКТОР / "+(mode==2?"ИГРА":"ОБЗОР"),"БЛОКИ / ДАННЫЕ","СОХРАНИТЬ","ОТМЕНА"})assertEquals(label,1,Collections.frequency(canvas.labels,label));
            assertFalse(canvas.labels.contains("МЕНЮ"));assertFalse(canvas.labels.contains("ПОДКЛЮЧЕНИЕ"));
            for(String label:canvas.labels){assertFalse(label.startsWith("THEME /"));assertFalse(label.startsWith("РЕЖИМ /"));assertFalse(label.matches("[0-9]{2}:[0-9]{2}"));}
            File folder=new File("build/screenshots");folder.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(folder,"editor-header-"+theme+"-"+mode+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            for(float x:new float[]{740,900,1080}){
                view.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,view.screenX(x),view.screenY(45),0));
                view.onTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,view.screenX(x),view.screenY(45),0));
            }
            assertArrayEquals(new int[]{1,1,1,0},clicks);
            view.editing=false;canvas.labels.clear();view.draw(canvas);
            assertTrue(canvas.labels.contains("МЕНЮ"));assertTrue(canvas.labels.contains("ПОДКЛЮЧЕНИЕ"));assertFalse(canvas.labels.contains("СОХРАНИТЬ"));bitmap.recycle();
        }
    }
}
