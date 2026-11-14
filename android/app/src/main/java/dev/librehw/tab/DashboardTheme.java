package dev.librehw.tab;
import android.graphics.Color;
final class DashboardTheme {
    final String name,description;
    final int primary,secondary,background,panel,glow;
    private DashboardTheme(String name,String description,String primary,String secondary,String background,String panel,String glow) {
        this.name=name;this.description=description;this.primary=Color.parseColor(primary);this.secondary=Color.parseColor(secondary);
        this.background=Color.parseColor(background);this.panel=Color.parseColor(panel);this.glow=Color.parseColor(glow);
    }
    boolean isNerv() { return name.equals("NERV") || name.startsWith("EVA-"); }
    static final DashboardTheme[] ALL={
        new DashboardTheme("JADE","Jade — мята и янтарь","#5EF2CA","#FFBA75","#070C15","#101B29","#25205049"),
        new DashboardTheme("EVA","EVA — кислотный зелёный и фиолетовый","#BCFF51","#B598FF","#0B0915","#171126","#352E165B"),
        new DashboardTheme("ARCTIC","Arctic — лёд и синий","#6FE3FF","#9DAEFF","#070E16","#0D2130","#30204060"),
        new DashboardTheme("SAKURA","Sakura — розовый и лаванда","#FF91C9","#C9ADFF","#140D16","#241827","#35481D3C"),
        new DashboardTheme("AMBER","Amber — медь и золото","#FFC36A","#FFE6B0","#120E09","#241D14","#35482F14"),
        new DashboardTheme("CRIMSON","Crimson — красный и ледяной голубой","#FF6D83","#7FDBFF","#130C10","#23151D","#354A1724"),
        new DashboardTheme("NERV","NERV — MAGI / угловая геометрия","#FF953D","#FF4657","#100B0C","#1E1115","#354D1518"),
        // Append only: theme_id is persisted as an array index.
        new DashboardTheme("EVA-00","EVA-00 — прототип / янтарь и голубой","#FFC45B","#80DFFF","#100E09","#211C12","#353E2C0C"),
        new DashboardTheme("EVA-01","EVA-01 — тестовый тип / фиолетовый и зелёный","#BE9AFF","#BEFF59","#0D0A15","#1C152C","#353B1C62"),
        new DashboardTheme("EVA-02","EVA-02 — серийный тип / красный и оранжевый","#FF6359","#FFD080","#140A0A","#291411","#354F1912")
    };
}
