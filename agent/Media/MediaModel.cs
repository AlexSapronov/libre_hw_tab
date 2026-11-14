internal record MediaView(string Id,string Title,string Artist,bool Playing,double Position,double Duration,bool CanPrevious,bool CanNext,bool CanToggle,bool CanSeek,bool HasThumbnail);
internal record MediaCommand(string Id,string Action,double? Position=null);
internal static class MediaPolicy
{
    internal static bool Chrome(string source)=>source.Equals("chrome.exe",StringComparison.OrdinalIgnoreCase)||source.Equals("Chrome",StringComparison.OrdinalIgnoreCase)||source.StartsWith("Chrome.",StringComparison.OrdinalIgnoreCase);
    internal static (double Position,double Duration) Timeline(double position,double start,double end,double elapsed,bool playing,double rate)
    {
        double duration=double.IsFinite(end-start)?Math.Max(0,end-start):0;
        double relative=double.IsFinite(position-start)?Math.Max(0,position-start):0;
        if(playing && double.IsFinite(elapsed) && elapsed>=0 && elapsed<86400 && double.IsFinite(rate) && rate>0)relative+=elapsed*rate;
        return (duration>0?Math.Min(relative,duration):relative,duration);
    }
    internal static bool Allowed(MediaView view,MediaCommand command)=>view.Id==command.Id && (command.Action switch {
        "previous"=>view.CanPrevious,"next"=>view.CanNext,"toggle"=>view.CanToggle,
        "seek"=>view.CanSeek && command.Position is double value && double.IsFinite(value) && value>=0 && value<=view.Duration,
        _=>false
    });
}
