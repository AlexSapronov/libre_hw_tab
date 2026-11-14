package dev.librehw.tab;

/** Inverse of the user's measured command/RPM curves, not a PWM sensor reading. */
final class FanCalibration {
    private static final float[][] TOP={{0,318},{1,319},{10,321},{20,460},{30,676},{40,883},{50,1249},{60,1249},{70,1417},{80,1587},{90,1738},{100,1962}};
    private static final float[][] SIDE={{1,406},{10,409},{20,534},{30,739},{40,1080},{50,1080},{60,1368},{70,1368},{80,1517},{90,1620},{100,1798}};
    // The 0..20% measurements are the same minimum speed, not real lower commands.
    private static final float[][] PUMP={{75,2700},{100,2900}};
    private static final float[][] GPU={{30,1150},{40,1502},{50,1854},{60,2207},{70,2558},{80,2912},{90,3260},{100,3600}};
    static float percent(String key,float rpm){
        if(!Float.isFinite(rpm)||rpm<0)return Float.NaN;
        float[][] curve=key.equals("fan")?TOP:key.equals("sideFan")?SIDE:key.equals("gpuFan")?GPU:key.equals("pump")?PUMP:null;
        if(curve==null)return Float.NaN;
        if(rpm==0)return 0;
        if(rpm<curve[0][1])return curve[0][0];
        for(int i=0;i<curve.length;i++){
            if(rpm==curve[i][1]){int last=i;while(last+1<curve.length&&curve[last+1][1]==rpm)last++;return (curve[i][0]+curve[last][0])/2;}
            if(rpm<curve[i][1]){float[] a=curve[i-1],b=curve[i];return a[0]+(rpm-a[1])/(b[1]-a[1])*(b[0]-a[0]);}
        }
        return 100;
    }
}
