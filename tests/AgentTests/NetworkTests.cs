using Xunit;
public class NetworkTests {
    [Fact] public void RatesUseElapsedTimeAndRejectResetsAndAdapterChanges(){
        var r=new TrafficRate();
        Assert.Null(r.Sample("a","Ethernet",100,100,0).DownloadBps);
        var next=r.Sample("a","Ethernet",1100,600,2);
        Assert.Equal(500d,next.DownloadBps);Assert.Equal(250d,next.UploadBps);
        Assert.Equal(0d,r.Sample("a","Ethernet",1100,600,3).DownloadBps);
        Assert.Null(r.Sample("a","Ethernet",1,1,4).DownloadBps);
        Assert.Null(r.Sample("b","Wi-Fi",10000,10000,5).DownloadBps);
        Assert.Null(r.Sample("b","Wi-Fi",20000,20000,20).DownloadBps);
        r.Reset();Assert.Null(r.Sample("b","Wi-Fi",21000,21000,21).DownloadBps);
    }
}
