using System.Globalization;
using System.Text;
using System.Text.Json;

internal record FrameSample(string Application, int Pid, string Chain, double Time, double Ms);
internal record ForegroundApp(int Pid, string Exe, string Path, string Title, bool Fullscreen);
internal record GameView(bool Active, string Status, string? Name = null, int? Pid = null, double? Fps = null,
    double? FrameTime = null, double? Low1 = null, double[]? Frames = null, bool Paused = false, string? SourceId = null);
internal record GameRules(string[]? Include = null, string[]? Exclude = null);

internal sealed class PresentCsv
{
    private Dictionary<string,int>? columns;
    internal FrameSample? Parse(string line)
    {
        if (line.Length > 32768) return null;
        var cells = Split(line.TrimStart('\uFEFF'));
        if (cells.Any(c => c.Equals("Application",StringComparison.OrdinalIgnoreCase)) && cells.Any(c => c.Equals("ProcessID",StringComparison.OrdinalIgnoreCase)))
        { columns = cells.Select((v,i)=>(v,i)).ToDictionary(x=>x.v,x=>x.i,StringComparer.OrdinalIgnoreCase); return null; }
        if (columns == null) return null;
        string Field(string name) => columns.TryGetValue(name,out var i) && i<cells.Count ? cells[i] : "";
        if (!int.TryParse(Field("ProcessID"),out var pid) || pid<=0 ||
            !double.TryParse(Field("TimeInSeconds"),NumberStyles.Float,CultureInfo.InvariantCulture,out var time) || !double.IsFinite(time) || time<0 ||
            !double.TryParse(Field("MsBetweenPresents"),NumberStyles.Float,CultureInfo.InvariantCulture,out var ms) || !double.IsFinite(ms) || ms<=0 || ms>60000) return null;
        var name = Field("Application"); var chain=Field("SwapChainAddress");
        if (string.IsNullOrWhiteSpace(name) || string.IsNullOrWhiteSpace(chain)) return null;
        return new(name,pid,chain,time,ms);
    }
    internal static List<string> Split(string line)
    {
        var result=new List<string>(); var cell=new StringBuilder(); bool quoted=false;
        for(int i=0;i<line.Length;i++) {
            if(line[i]=='"') { if(quoted && i+1<line.Length && line[i+1]=='"') {cell.Append('"');i++;} else quoted=!quoted; }
            else if(line[i]==',' && !quoted) { result.Add(cell.ToString());cell.Clear(); }
            else cell.Append(line[i]);
        }
        result.Add(cell.ToString()); return result;
    }
}

internal sealed class FrameHistory
{
    private readonly Dictionary<(int,string),List<(FrameSample Frame,DateTimeOffset Received)>> buffers=new();
    private DateTimeOffset nextPrune;
    internal void Add(FrameSample frame,DateTimeOffset now)
    {
        if(now>=nextPrune) {
            foreach(var key in buffers.Where(k=>now-k.Value[^1].Received>TimeSpan.FromSeconds(12)).Select(k=>k.Key).ToArray()) buffers.Remove(key);
            nextPrune=now.AddSeconds(5);
        }
        var key2=(frame.Pid,frame.Chain);
        if(!buffers.TryGetValue(key2,out var list)) {
            if(buffers.Count>=32) buffers.Remove(buffers.MinBy(k=>k.Value[^1].Received).Key);
            buffers[key2]=list=new();
        }
        if(list.Count>0 && !list[^1].Frame.Application.Equals(frame.Application,StringComparison.OrdinalIgnoreCase)) list.Clear();
        if(list.Count>0 && frame.Time<=list[^1].Frame.Time) return;
        list.Add((frame,now));
        int remove=0;
        while(remove<list.Count && (frame.Time-list[remove].Frame.Time>10 || list.Count-remove>16384)) remove++;
        if(remove>0) list.RemoveRange(0,remove);
    }
    internal GameView? ForProcess(int pid,DateTimeOffset now)
    {
        var list=buffers.Where(k=>k.Key.Item1==pid && now-k.Value[^1].Received<TimeSpan.FromSeconds(2))
            .Select(k=>k.Value).OrderByDescending(l=>l.Count(f=>l[^1].Frame.Time-f.Frame.Time<=1)).FirstOrDefault();
        if(list==null) return null;
        var last=list[^1].Frame;
        var recent=list.Where(f=>last.Time-f.Frame.Time<=1).Select(f=>f.Frame.Ms).ToArray();
        if(recent.Length<3) return null;
        var all=list.Select(f=>f.Frame.Ms).ToArray();
        double? low=all.Length>=60 && last.Time-list[0].Frame.Time>=1
            ? 1000/all.OrderByDescending(v=>v).Take(Math.Max(1,(int)Math.Ceiling(all.Length*.01))).Average() : null;
        return new(false,"ready",last.Application,pid,1000/recent.Average(),recent.Average(),low,
            all.TakeLast(180).ToArray(),false,$"{pid}:{last.Chain}");
    }
    internal void Clear() => buffers.Clear();
}

internal sealed class GameDetector
{
    private int candidate; private DateTimeOffset since, lastFocused;
    private GameView? active;
    private static readonly HashSet<string> Known=new(StringComparer.OrdinalIgnoreCase) {"wow.exe","wowclassic.exe","wowb.exe","zenlesszonezero.exe","genshinimpact.exe","starrail.exe"};
    private static readonly HashSet<string> Ignored=new(StringComparer.OrdinalIgnoreCase) {
        "dwm.exe","explorer.exe","chrome.exe","msedge.exe","firefox.exe","opera.exe","brave.exe","vivaldi.exe","iexplore.exe",
        "discord.exe","steam.exe","steamwebhelper.exe","epicgameslauncher.exe","battle.net.exe","hoyoplay.exe","launcher.exe",
        "vlc.exe","mpc-hc64.exe","potplayer64.exe","obs64.exe","code.exe","devenv.exe","blender.exe","unrealeditor.exe","unity.exe",
        "msedgewebview2.exe","spotify.exe","slack.exe","telegram.exe","lively.exe","wallpaper64.exe","wallpaper32.exe","taskmgr.exe","windowsterminal.exe","mediaplayer.exe",
        "applicationframehost.exe","shellexperiencehost.exe","searchhost.exe","systemsettings.exe","librehwagent.exe","presentmon.exe","wmplayer.exe","mpv.exe"
    };
    internal static bool Eligible(ForegroundApp app,GameRules rules)
    {
        if(rules.Exclude?.Contains(app.Exe,StringComparer.OrdinalIgnoreCase)==true) return false;
        if(rules.Include?.Contains(app.Exe,StringComparer.OrdinalIgnoreCase)==true) return true;
        if(Ignored.Contains(app.Exe)) return false;
        string path=app.Path.Replace('/','\\');
        return Known.Contains(app.Exe) || path.Contains("\\steamapps\\common\\",StringComparison.OrdinalIgnoreCase)
            || path.Contains("\\Epic Games\\",StringComparison.OrdinalIgnoreCase) || path.Contains("\\Games\\",StringComparison.OrdinalIgnoreCase)
            || path.Contains("\\HoYoPlay\\",StringComparison.OrdinalIgnoreCase) || app.Fullscreen;
    }
    internal GameView Update(ForegroundApp? foreground,GameView? frames,DateTimeOffset now,bool activeAlive,GameRules rules)
    {
        bool qualifying=foreground!=null && frames?.Fps>=10 && Eligible(foreground,rules);
        if(qualifying) {
            if(candidate!=foreground!.Pid) { candidate=foreground.Pid;since=now; }
            if(active?.Pid==foreground.Pid || now-since>=TimeSpan.FromSeconds(3)) {
                active=frames! with {Active=true,Name=string.IsNullOrWhiteSpace(foreground.Title)?foreground.Exe:foreground.Title[..Math.Min(160,foreground.Title.Length)]};
                lastFocused=now; return active;
            }
        } else { candidate=0; }
        if(active!=null && activeAlive && now-lastFocused<TimeSpan.FromSeconds(10))
            return active with {Fps=null,FrameTime=null,Low1=null,Frames=Array.Empty<double>(),Paused=true};
        active=null; return new(false,"idle");
    }
    internal int? ActivePid => active?.Pid;
    internal void Reset() {active=null;candidate=0;}
}
