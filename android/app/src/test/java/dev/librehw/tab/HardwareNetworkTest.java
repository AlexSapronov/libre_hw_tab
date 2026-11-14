package dev.librehw.tab;
import android.graphics.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HardwareNetworkTest {
    @Test public void splitsExistingPanelAndPreservesOtherCustomPositions() throws Exception {
        DashboardLayout l=DashboardLayout.parse("{version:2,positions:{'game.side':[944,125,1164,690],'overview.cpu':[36,125,450,460]},disabled:['power']}");
        assertTrue(l.valid(true));assertTrue(l.valid(false));assertEquals(450,l.rect("cpu",false).right,0);
        assertFalse(RectF.intersects(l.rect("gpu",true),l.rect("cpu",true)));assertFalse(l.metric("power"));
        assertEquals(3,new JSONObject(l.json()).getInt("version"));assertEquals(l.json(),DashboardLayout.parse(l.json()).json());
        l.hidden.add("game.gpu");assertTrue(l.visible("cpu",true));assertFalse(l.visible("gpu",true));
        l.positions.put("game.cpu",new RectF(944,430,1164,690));assertTrue(l.valid(true));
    }
    @Test public void migratesHiddenSmallAndHorizontalPanelsWithoutResettingLayout(){
        DashboardLayout hidden=DashboardLayout.parse("{version:2,hidden:['game.side','overview.side']}");
        assertFalse(hidden.visible("gpu",true));assertFalse(hidden.visible("cpu",true));assertFalse(hidden.visible("network",false));
        DashboardLayout small=DashboardLayout.parse("{version:2,positions:{'overview.side':[944,125,1164,235]}}");
        assertEquals(110,small.rect("side",false).height(),0);assertFalse(small.visible("network",false));
        DashboardLayout shortPanel=DashboardLayout.parse("{version:2,positions:{'overview.side':[944,125,1164,363]}}");
        assertTrue(shortPanel.valid(false));assertEquals(110,shortPanel.rect("network",false).height(),.01);
        DashboardLayout wide=DashboardLayout.parse("{version:2,hidden:['game.main','game.audio','game.memory','game.history'],positions:{'game.side':[36,125,736,300]}}");
        assertTrue(wide.valid(true));assertEquals(341,wide.rect("gpu",true).width(),0);
    }
    @Test public void networkAndIndependentHardwareRenderInBothModes() throws Exception {
        DashboardView v=new DashboardView(RuntimeEnvironment.getApplication(),()->{},()->{});v.layout(0,0,1340,800);v.themeId=8;v.animations=false;
        JSONObject data=DemoData.sample();v.accept(data,"",true);v.mode=1;render(v,"network-overview");
        v.accept(new JSONObject(data.toString()).put("media",MediaPanelTest.media()),"",true);render(v,"network-overview-media");
        v.mode=2;render(v,"hardware-split-media");v.clear();v.accept(data,"",true);render(v,"hardware-split");
        v.mode=1;data.remove("network");v.accept(data,"",false);render(v,"network-old-agent");
        data.put("network",new JSONObject().put("status","offline"));v.accept(data,"",false);render(v,"network-offline");
        assertEquals("8.00",DashboardView.traffic(1000000));assertEquals("0.00",DashboardView.traffic(0));assertEquals("—",DashboardView.traffic(Double.NaN));
    }
    private void render(DashboardView v,String name)throws Exception{
        Bitmap b=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File f=new File("build/screenshots");f.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(f,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
}
