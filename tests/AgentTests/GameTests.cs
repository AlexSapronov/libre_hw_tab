using Xunit;

public class GameTests
{
    private readonly DateTimeOffset now=DateTimeOffset.Parse("2026-11-14T00:00:00Z");
    private static ForegroundApp Wow()=>new(42,"WowClassic.exe",@"D:\World of Warcraft\_classic_\WowClassic.exe","World of Warcraft",false);
    private static GameView Frames(int pid=42)=>new(false,"ready","test.exe",pid,144,6.94,120,new[]{6.94},false,$"{pid}:0x1");
    [Fact] public void RequiresFramesAndThreeSecondsThenSurvivesBriefAltTabButExitsOnProcessExit()
    {
        var d=new GameDetector();var rules=new GameRules();
        Assert.False(d.Update(Wow(),null,now,false,rules).Active);
        Assert.False(d.Update(Wow(),Frames(),now,false,rules).Active);
        Assert.False(d.Update(Wow(),Frames(),now.AddSeconds(2),false,rules).Active);
        Assert.True(d.Update(Wow(),Frames(),now.AddSeconds(3),false,rules).Active);
        var tab=d.Update(null,null,now.AddSeconds(5),true,rules);Assert.True(tab.Active);Assert.True(tab.Paused);Assert.Null(tab.Fps);
        Assert.True(d.Update(Wow(),Frames(),now.AddSeconds(6),true,rules).Active);
        Assert.False(d.Update(null,null,now.AddSeconds(7),false,rules).Active);
    }
    [Fact] public void ExcludesVideoBrowsersLaunchersAndDccEvenWhenFullscreen()
    {
        foreach(var exe in new[]{"chrome.exe","msedge.exe","firefox.exe","vlc.exe","steam.exe","obs64.exe","blender.exe","discord.exe"})
            Assert.False(GameDetector.Eligible(new(9,exe,@"D:\Games\"+exe,"video",true),new()));
        Assert.True(GameDetector.Eligible(new(9,"newgame.exe",@"D:\Steam\steamapps\common\Title\game.exe","Title",false),new()));
        Assert.False(GameDetector.Eligible(Wow(),new(Exclude:new[]{"WowClassic.exe"})));
        Assert.True(GameDetector.Eligible(new(9,"custom.exe","custom.exe","Title",false),new(Include:new[]{"custom.exe"})));
    }
    [Fact] public void GraceExpiresAndSwitchingGamesCannotMixIdentities()
    {
        var d=new GameDetector();d.Update(Wow(),Frames(),now,false,new());d.Update(Wow(),Frames(),now.AddSeconds(3),true,new());
        Assert.False(d.Update(null,null,now.AddSeconds(14),true,new()).Active);
        var other=new ForegroundApp(99,"ZenlessZoneZero.exe","z.exe","ZZZ",false);
        Assert.False(d.Update(other,Frames(99),now.AddSeconds(15),true,new()).Active);
        Assert.Equal(99,d.Update(other,Frames(99),now.AddSeconds(18),true,new()).Pid);
    }
    [Fact] public void ParsesActualV1HeadersAndQuotedCsvRejectsNaNAndMissingColumns()
    {
        var parser=new PresentCsv();Assert.Null(parser.Parse("Application,ProcessID,SwapChainAddress,Runtime,TimeInSeconds,msBetweenPresents"));
        var frame=parser.Parse("\"Title, game.exe\",42,0x1,DXGI,1.000,6.94");Assert.Equal("Title, game.exe",frame!.Application);Assert.Equal(6.94,frame.Ms);
        Assert.Null(parser.Parse("title.exe,42,0x1,DXGI,1.1,NaN"));Assert.Null(parser.Parse("title.exe,42"));
        Assert.Null(parser.Parse("title.exe,42,0x1,DXGI,1.1,-2"));
    }
    [Fact] public void ComputesRealFrameStatisticsAndNeverMergesSwapchainsOrStaleFrames()
    {
        var history=new FrameHistory();
        for(int i=0;i<120;i++)history.Add(new("wow.exe",42,"main",i/60.0, i==100?30:16.6667),now);
        for(int i=0;i<6;i++)history.Add(new("wow.exe",42,"overlay",i/10.0,100),now);
        var view=history.ForProcess(42,now)!;Assert.InRange(view.Fps!.Value,58,60.1);Assert.InRange(view.Low1!.Value,33,44);
        Assert.Equal("42:main",view.SourceId);Assert.Equal(120,view.Frames!.Length);
        Assert.Null(history.ForProcess(42,now.AddSeconds(3)));Assert.Null(history.ForProcess(99,now));
        history.Clear();Assert.Null(history.ForProcess(42,now));
    }
}
