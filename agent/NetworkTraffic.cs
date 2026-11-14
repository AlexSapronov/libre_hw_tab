using System.Diagnostics;
using System.Net.NetworkInformation;
using System.Net.Sockets;

record NetworkView(string Status, string? Name, double? DownloadBps, double? UploadBps);

// A new adapter, reset counter or interrupted sampling starts a new baseline.
sealed class TrafficRate
{
    private string? id;
    private long received, sent;
    private double time;
    public NetworkView Sample(string key,string name,long rx,long tx,double now)
    {
        double elapsed=now-time;
        bool valid=id==key && elapsed>0 && elapsed<=5 && rx>=received && tx>=sent;
        var value=new NetworkView(valid?"ready":"warming",name,valid?(rx-received)/elapsed:null,valid?(tx-sent)/elapsed:null);
        id=key;received=rx;sent=tx;time=now;return value;
    }
    public void Reset(){id=null;}
}
sealed class NetworkTraffic : BackgroundService
{
    private NetworkView current=new("warming",null,null,null);
    public NetworkView Current=>Volatile.Read(ref current);
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var rates=new TrafficRate();
        using var timer=new PeriodicTimer(TimeSpan.FromSeconds(1));
        do {
            try {
                // One active gateway adapter avoids summing physical + tunnel traffic twice.
                var nic=NetworkInterface.GetAllNetworkInterfaces()
                    .Where(n=>n.OperationalStatus==OperationalStatus.Up &&
                        n.NetworkInterfaceType is NetworkInterfaceType.Ethernet or NetworkInterfaceType.Wireless80211)
                    .Where(n=>n.GetIPProperties().GatewayAddresses.Any(g=>
                        (g.Address.AddressFamily is AddressFamily.InterNetwork or AddressFamily.InterNetworkV6) &&
                        !g.Address.Equals(System.Net.IPAddress.Any) && !g.Address.Equals(System.Net.IPAddress.IPv6Any)))
                    .OrderByDescending(n=>n.Speed).ThenBy(n=>n.Id,StringComparer.Ordinal).FirstOrDefault();
                if(nic==null){rates.Reset();Volatile.Write(ref current,new("offline",null,null,null));}
                else {
                    var stats=nic.GetIPStatistics();
                    Volatile.Write(ref current,rates.Sample(nic.Id,nic.Name,stats.BytesReceived,stats.BytesSent,Stopwatch.GetTimestamp()/(double)Stopwatch.Frequency));
                }
            } catch {rates.Reset();Volatile.Write(ref current,new("unavailable",null,null,null));}
            try {if(!await timer.WaitForNextTickAsync(stop))break;} catch(OperationCanceledException){break;}
        } while(!stop.IsCancellationRequested);
    }
}
