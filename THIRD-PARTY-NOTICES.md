# Third-party notices

The Windows companion uses LibreHardwareMonitorLib 0.9.6, licensed under Mozilla Public License 2.0.
Source, license, and notices: https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/tree/v0.9.6
License text: https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LICENSE
Additional notices: https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/THIRD-PARTY-NOTICES.txt
The dependency is used unmodified; its corresponding source is available at the tag above.

Dependencies transitively included by NuGet retain their respective licenses and notices.
PawnIO is a separate optional prerequisite installed by the user from https://pawnio.eu/; its installer is not redistributed by this project.

Android components are built against the Android SDK; the app uses only platform APIs.

QR generation: QRCoder 1.6.0, MIT license, https://github.com/Shane32/QRCoder .
Android QR scanner: ZXing Android Embedded 4.3.0 (JourneyApps), Apache-2.0, https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0 .
ZXing core: Apache-2.0, https://github.com/zxing/zxing .
AndroidX Activity and transitive AndroidX dependencies: Apache-2.0, https://android.googlesource.com/platform/frameworks/support/ .

PresentMon standalone 2.6.0 x64 (unmodified official binary), MIT license, https://github.com/GameTechDev/PresentMon/tree/v2.6.0 . Full license accompanies the binary as PresentMon-LICENSE.txt; binary SHA-256 b2a706bc6ad475749e3b7e3409263aa1e6906d45bdcf993f6dbc0f660188f1af.

Windows audio mixer: NAudio.Wasapi / NAudio.Core 2.2.1, MIT license, https://github.com/naudio/NAudio/tree/v2.2.1 . Windows Core Audio session volumes and endpoint microphone mute are used; audio is not recorded.
