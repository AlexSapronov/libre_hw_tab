using System.Net.NetworkInformation;
using System.Net.Sockets;
internal record WakeTarget(string Address,string Mac,string Broadcast);
internal static class NetworkWake
{
    internal static WakeTarget[] Read()
    {
        try {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(n=>n.OperationalStatus==OperationalStatus.Up && (n.NetworkInterfaceType==NetworkInterfaceType.Ethernet || n.NetworkInterfaceType==NetworkInterfaceType.Wireless80211))
                .Where(n=>!new[]{"virtual","vpn","wireguard","wintun","tap-","hyper-v"}.Any(v=>(n.Name+" "+n.Description).Contains(v,StringComparison.OrdinalIgnoreCase)))
                .SelectMany(n=>n.GetIPProperties().UnicastAddresses.Where(a=>a.Address.AddressFamily==AddressFamily.InterNetwork).Select(a=>{
                    var ip=a.Address.GetAddressBytes();var mask=a.IPv4Mask.GetAddressBytes();var broadcast=new byte[4];
                    for(int i=0;i<4;i++)broadcast[i]=(byte)(ip[i]|~mask[i]);
                    return new WakeTarget(a.Address.ToString(),n.GetPhysicalAddress().ToString(),new System.Net.IPAddress(broadcast).ToString());
                })).Where(x=>x.Mac.Length==12).Take(8).ToArray();
        }catch{return Array.Empty<WakeTarget>();}
    }
    internal static readonly Lazy<WakeTarget[]> Targets=new(Read);
}
