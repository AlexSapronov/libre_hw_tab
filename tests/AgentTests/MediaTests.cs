using Xunit;
public class MediaTests
{
    private static MediaView View()=>new("current","video","channel",true,10,100,false,true,true,true,false);
    [Fact] public void OnlyChromeSessionsAreEligible()
    {
        Assert.True(MediaPolicy.Chrome("chrome.exe"));Assert.True(MediaPolicy.Chrome("Chrome"));Assert.True(MediaPolicy.Chrome("Chrome.ABC"));
        foreach(var app in new[]{"msedge.exe","spotify.exe","vlc.exe","notchrome.exe",""})Assert.False(MediaPolicy.Chrome(app));
    }
    [Fact] public void TimelineExtrapolatesOnlyPlayingClampsAndSupportsNonzeroStart()
    {
        Assert.Equal((15d,100d),MediaPolicy.Timeline(110,100,200,5,true,1));
        Assert.Equal((10d,100d),MediaPolicy.Timeline(110,100,200,5,false,1));
        Assert.Equal((100d,100d),MediaPolicy.Timeline(199,100,200,50,true,1));
        Assert.Equal((0d,0d),MediaPolicy.Timeline(double.NaN,0,double.NaN,0,false,1));
    }
    [Fact] public void CommandsAreBoundToCurrentVideoCapabilitiesAndSeekRange()
    {
        var view=View();Assert.True(MediaPolicy.Allowed(view,new("current","next")));
        Assert.False(MediaPolicy.Allowed(view,new("old","next")));Assert.False(MediaPolicy.Allowed(view,new("current","previous")));
        Assert.True(MediaPolicy.Allowed(view,new("current","seek",50)));Assert.False(MediaPolicy.Allowed(view,new("current","seek",101)));
        Assert.False(MediaPolicy.Allowed(view,new("current","seek",double.NaN)));Assert.False(MediaPolicy.Allowed(view,new("current","seek",-1)));
        Assert.False(MediaPolicy.Allowed(view,new("current","launch")));Assert.False(MediaPolicy.Allowed(view with{CanSeek=false},new("current","seek",50)));
    }
}
