using Windows.Media.Control;
using Windows.Storage.Streams;
using System.Drawing;
using System.Drawing.Imaging;

internal sealed class MediaService : BackgroundService
{
    private readonly SemaphoreSlim gate=new(1,1);
    private GlobalSystemMediaTransportControlsSessionManager? manager;
    private GlobalSystemMediaTransportControlsSession? session;
    private MediaView? view;
    private sealed record Artwork(string Id,byte[] Data);
    private Artwork? thumbnail;
    private string metadataKey="",title="",artist="",id="";
    private DateTimeOffset nextMetadata,pausedSince;
    private bool wasPlaying;
    internal MediaView? Current=>Volatile.Read(ref view);
    internal byte[]? Thumbnail(string expected)=>Volatile.Read(ref thumbnail) is {} art && art.Id==expected && Current?.Id==expected?art.Data:null;
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        while(!stop.IsCancellationRequested) {
            await gate.WaitAsync(stop);
            try {
                manager??=await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
                var sessions=manager.GetSessions().Where(s=>MediaPolicy.Chrome(s.SourceAppUserModelId)).ToArray();
                var selected=sessions.FirstOrDefault(s=>s.GetPlaybackInfo().PlaybackStatus==GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                    ?? sessions.FirstOrDefault(s=>s.SourceAppUserModelId==session?.SourceAppUserModelId);
                if(selected==null){Clear();continue;}
                if(selected.SourceAppUserModelId!=session?.SourceAppUserModelId)Clear();session=selected;
                var info=selected.GetPlaybackInfo();bool playing=info.PlaybackStatus==GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
                var now=DateTimeOffset.UtcNow;
                if(playing){wasPlaying=true;pausedSince=default;}
                else if(wasPlaying && info.PlaybackStatus==GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused){if(pausedSince==default)pausedSince=now;}
                else {Clear();continue;}
                if(!playing && now-pausedSince>TimeSpan.FromSeconds(30)){Volatile.Write(ref view,null);continue;}
                if(now>=nextMetadata) {
                    var properties=await selected.TryGetMediaPropertiesAsync();
                    string key=properties.Title+"\n"+properties.Artist+"\n"+properties.AlbumTitle+"\n"+properties.TrackNumber;
                    if(key!=metadataKey || id.Length==0) {
                        id=Guid.NewGuid().ToString("N");metadataKey=key;title=Limit(properties.Title,180);artist=Limit(properties.Artist,100);
                        Volatile.Write(ref thumbnail,null);
                        if(properties.Thumbnail!=null)try{var bytes=await ReadThumbnail(properties.Thumbnail);if(bytes!=null)Volatile.Write(ref thumbnail,new Artwork(id,bytes));}catch{}
                    }
                    nextMetadata=now.AddSeconds(2);
                }
                if(string.IsNullOrWhiteSpace(title)){Volatile.Write(ref view,null);continue;}
                var timeline=selected.GetTimelineProperties();
                var (position,duration)=MediaPolicy.Timeline(timeline.Position.TotalSeconds,timeline.StartTime.TotalSeconds,timeline.EndTime.TotalSeconds,
                    (now-timeline.LastUpdatedTime).TotalSeconds,playing,info.PlaybackRate??1);
                if(!playing && duration>0 && position>=duration-.5){Volatile.Write(ref view,null);continue;}
                var controls=info.Controls;
                Volatile.Write(ref view,new(id,title,artist,playing,position,duration,controls.IsPreviousEnabled,controls.IsNextEnabled,
                    playing?controls.IsPauseEnabled:controls.IsPlayEnabled,controls.IsPlaybackPositionEnabled && duration>0,thumbnail!=null));
            }catch {Clear();manager=null;}
            finally {gate.Release();try{await Task.Delay(500,stop);}catch(OperationCanceledException){}}
        }
    }
    internal async Task<bool> Control(MediaCommand command)
    {
        if(!await gate.WaitAsync(1500))return false;
        try {
            if(Current is not {} current || session==null || !MediaPolicy.Allowed(current,command))return false;
            // A command queued for an old video must never affect the next one.
            var properties=await session.TryGetMediaPropertiesAsync();
            if(metadataKey!=properties.Title+"\n"+properties.Artist+"\n"+properties.AlbumTitle+"\n"+properties.TrackNumber)return false;
            return command.Action switch {
                "previous"=>await session.TrySkipPreviousAsync(),"next"=>await session.TrySkipNextAsync(),
                "toggle"=>current.Playing?await session.TryPauseAsync():await session.TryPlayAsync(),
                "seek"=>await session.TryChangePlaybackPositionAsync(TimeSpan.FromSeconds(session.GetTimelineProperties().StartTime.TotalSeconds+command.Position!.Value).Ticks),_=>false
            };
        }catch{return false;}finally{gate.Release();}
    }
    private void Clear(){session=null;metadataKey="";id="";title="";artist="";nextMetadata=default;wasPlaying=false;pausedSince=default;Volatile.Write(ref thumbnail,null);Volatile.Write(ref view,null);}
    private static string Limit(string value,int length)=>value[..Math.Min(value.Length,length)];
    private static async Task<byte[]?> ReadThumbnail(IRandomAccessStreamReference reference)
    {
        using var stream=await reference.OpenReadAsync();if(stream.Size==0 || stream.Size>4*1024*1024)return null;
        using var reader=new DataReader(stream);uint size=(uint)stream.Size;if(await reader.LoadAsync(size)!=size)return null;
        byte[] source=new byte[size];reader.ReadBytes(source);
        using var input=new MemoryStream(source);using var image=Image.FromStream(input);
        if(image.Width>8192 || image.Height>8192)return null;
        double scale=Math.Min(1,Math.Min(256.0/image.Width,144.0/image.Height));
        using var resized=new Bitmap(Math.Max(1,(int)(image.Width*scale)),Math.Max(1,(int)(image.Height*scale)));
        using(var graphics=Graphics.FromImage(resized))graphics.DrawImage(image,0,0,resized.Width,resized.Height);
        using var output=new MemoryStream();resized.Save(output,ImageFormat.Jpeg);return output.Length<=131072?output.ToArray():null;
    }
}
