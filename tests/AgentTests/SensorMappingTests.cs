using LibreHardwareMonitor.Hardware;
using Moq;
using Xunit;

public class SensorMappingTests
{
    [Fact] public void SelectsPhysicalRamEvenWhenVirtualMemoryIsFirst()
    {
        var virtualMemory = Hardware("Virtual Memory", "/vram", HardwareType.Memory);
        var physicalMemory = Hardware("Total Memory", "/ram", HardwareType.Memory);
        var dimm = Hardware("DIMM #1", "/memory/dimm/0", HardwareType.Memory);
        Assert.Same(physicalMemory.Object, SensorMapping.PhysicalMemory(new[] { virtualMemory.Object, dimm.Object, physicalMemory.Object }));
        Assert.Null(SensorMapping.PhysicalMemory(new[] { virtualMemory.Object, dimm.Object }));
    }
    [Fact] public void ShowsInstalledCapacityWithoutMixingVirtualOrUsableMemory()
    {
        var physical = new Device("Total Memory",null,40,null,null,null,12.4f,31.2f);
        var result = PhysicalMemoryInfo.WithInstalledCapacity(physical,32)!;
        Assert.Equal(32f,result.Total); Assert.Equal(31.2f,result.UsableTotal);
        Assert.Equal(12.4f,result.Used); Assert.Equal(40f,result.Load);
        Assert.Null(PhysicalMemoryInfo.WithInstalledCapacity(null,32));
        Assert.Equal(31.2f,PhysicalMemoryInfo.WithInstalledCapacity(physical,null)!.Total);
    }
    [Fact] public void TraversesSuperIoAndMapsConfirmedWorkstationHeaders()
    {
        var board = Hardware("ASUS TUF GAMING B650M-PLUS", "/mainboard", HardwareType.Motherboard);
        var chip = Hardware("Nuvoton NCT6799D", "/lpc/nct6799d/0", HardwareType.SuperIO);
        var gpu = Hardware("RTX 5070 Ti", "/gpu-nvidia/0", HardwareType.GpuNvidia);
        chip.SetupGet(h => h.Sensors).Returns(new[] { Fan("Fan #1",765), Fan("Fan #2",1467), Fan("Fan #7",2705), Fan("Fan #3",0) });
        board.SetupGet(h => h.SubHardware).Returns(new[] { chip.Object });
        gpu.SetupGet(h => h.Sensors).Returns(new[] { Fan("Fan #1",5000) });
        var selected = SensorMapping.ReadCooling(new[] { board.Object, gpu.Object });
        Assert.True(selected.WorkstationProfile);
        Assert.Equal(765f, selected.Radiator!.Rpm); Assert.Equal(1467f, selected.Side!.Rpm); Assert.Equal(2705f,selected.Pump!.Rpm);
        Assert.Equal(4,selected.Fans.Length);
    }
    [Fact] public void NeverGuessesNumberedFansOnOtherBoardsOrSubstitutesPumpForMissingRadiator()
    {
        var fans = new[] { new FanReading("one","Fan #1","NCT6799D","Another board",750), new FanReading("seven","Fan #7","NCT6799D","Another board",2700) };
        var selected = SensorMapping.SelectCooling(fans);
        Assert.Null(selected.Radiator); Assert.Null(selected.Pump);
        var pumpOnly = SensorMapping.SelectCooling(new[] { fans[1] with { Board="ASUS TUF GAMING B650M-PLUS" } });
        Assert.Null(pumpOnly.Radiator); Assert.Equal(2700f,pumpOnly.Pump!.Rpm);
        var stopped = SensorMapping.SelectCooling(new[] { fans[0] with { Name="CPU Fan", Rpm=0 } });
        Assert.Equal(0f,stopped.Radiator!.Rpm);
    }
    private static Mock<IHardware> Hardware(string name,string id,HardwareType type)
    {
        var mock = new Mock<IHardware>(); mock.SetupGet(h=>h.Name).Returns(name); mock.SetupGet(h=>h.Identifier).Returns(new Identifier(id.TrimStart('/').Split('/')));
        mock.SetupGet(h=>h.HardwareType).Returns(type); mock.SetupGet(h=>h.Sensors).Returns(Array.Empty<ISensor>()); mock.SetupGet(h=>h.SubHardware).Returns(Array.Empty<IHardware>()); return mock;
    }
    private static ISensor Fan(string name,float rpm)
    {
        var mock = new Mock<ISensor>(); mock.SetupGet(s=>s.Name).Returns(name); mock.SetupGet(s=>s.SensorType).Returns(SensorType.Fan); mock.SetupGet(s=>s.Value).Returns(rpm);
        mock.SetupGet(s=>s.Identifier).Returns(new Identifier("test",name)); return mock.Object;
    }
}
