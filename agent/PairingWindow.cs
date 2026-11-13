using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text.Json;
using QRCoder;

internal static class PairingWindow
{
    internal static string[] Addresses(int port) => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
        .OrderByDescending(n => n.GetIPProperties().GatewayAddresses.Count > 0)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses)
        .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork && IsPrivate(a.Address.GetAddressBytes()))
        .Select(a => $"http://{a.Address}:{port}").Distinct().Take(16).ToArray();

    private static bool IsPrivate(byte[] ip) => ip[0] == 10 || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) || (ip[0] == 192 && ip[1] == 168);

    internal static string Payload(Connection config, string[] addresses) => JsonSerializer.Serialize(new { app = "libre-hw-tab", version = 1, addresses, token = config.Token });
    internal static byte[] Qr(string payload)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        using var code = new PngByteQRCode(data);
        return code.GetGraphic(8);
    }

    internal static void Run(bool test)
    {
        if (test)
        {
            var payload = Payload(new Connection(8750, new string('a',32)), new[] { "http://192.168.0.100:8750", "http://10.8.1.2:8750" });
            using var parsed = JsonDocument.Parse(payload);
            if (parsed.RootElement.GetProperty("app").GetString() != "libre-hw-tab" || Qr(payload).Length < 100) throw new InvalidOperationException("QR self-test failed");
            return;
        }
        System.Windows.Forms.Application.EnableVisualStyles();
        System.Windows.Forms.Application.SetCompatibleTextRenderingDefault(false);
        var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LibreHwTab", "connection.json");
        if (!File.Exists(path)) { System.Windows.Forms.MessageBox.Show("Сначала запустите Install.cmd.", "Libre HW Tab"); return; }
        var config = JsonSerializer.Deserialize<Connection>(File.ReadAllText(path))!;
        var addresses = Addresses(config.Port);
        using var form = new System.Windows.Forms.Form {
            Text = "Libre HW Tab · Подключение планшета", ClientSize = new System.Drawing.Size(680, 760),
            MinimumSize = new System.Drawing.Size(620, 700), StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen,
            BackColor = System.Drawing.Color.FromArgb(10, 17, 28), ForeColor = System.Drawing.Color.White,
            Font = new System.Drawing.Font("Segoe UI", 11), AutoScaleMode = System.Windows.Forms.AutoScaleMode.Dpi
        };
        var layout = new System.Windows.Forms.TableLayoutPanel { Dock = System.Windows.Forms.DockStyle.Fill, Padding = new System.Windows.Forms.Padding(28), ColumnCount = 1, RowCount = 5 };
        layout.RowStyles.Add(new(System.Windows.Forms.SizeType.Absolute, 90));
        layout.RowStyles.Add(new(System.Windows.Forms.SizeType.Absolute, 44));
        layout.RowStyles.Add(new(System.Windows.Forms.SizeType.Percent, 100));
        layout.RowStyles.Add(new(System.Windows.Forms.SizeType.Absolute, 65));
        layout.RowStyles.Add(new(System.Windows.Forms.SizeType.Absolute, 45));
        var title = new System.Windows.Forms.Label { Text = "Подключи планшет за один скан\nВ приложении: ПОДКЛЮЧЕНИЕ → Сканировать QR", Dock = System.Windows.Forms.DockStyle.Fill, AutoSize = true };
        var networks = new System.Windows.Forms.ComboBox { Dock = System.Windows.Forms.DockStyle.Fill, DropDownStyle = System.Windows.Forms.ComboBoxStyle.DropDownList };
        networks.Items.Add("Автоматически — все локальные сети");
        foreach (var address in addresses) networks.Items.Add(address);
        var picture = new System.Windows.Forms.PictureBox { Dock = System.Windows.Forms.DockStyle.Fill, SizeMode = System.Windows.Forms.PictureBoxSizeMode.Zoom, BackColor = System.Drawing.Color.White };
        var hint = new System.Windows.Forms.Label { Dock = System.Windows.Forms.DockStyle.Fill, TextAlign = System.Drawing.ContentAlignment.MiddleLeft, Text = addresses.Length == 0 ? "Нет локального IPv4-адреса. Подключите ПК к домашней сети и откройте это окно заново." : "ПК и планшет должны быть в одной домашней сети.\nАдрес и ключ сохранятся автоматически." };
        var close = new System.Windows.Forms.Button { Text = "Готово", Dock = System.Windows.Forms.DockStyle.Right, Width = 140, FlatStyle = System.Windows.Forms.FlatStyle.Flat };
        close.Click += (_, _) => form.Close();
        networks.SelectedIndexChanged += (_, _) => {
            var selected = networks.SelectedIndex == 0 ? addresses : new[] { addresses[networks.SelectedIndex - 1] };
            var old = picture.Image;
            using var stream = new MemoryStream(Qr(Payload(config, selected)));
            using var image = System.Drawing.Image.FromStream(stream);
            picture.Image = new System.Drawing.Bitmap(image);
            old?.Dispose();
        };
        layout.Controls.Add(title); layout.Controls.Add(networks); layout.Controls.Add(picture); layout.Controls.Add(hint); layout.Controls.Add(close);
        form.Controls.Add(layout); networks.SelectedIndex = 0;
        System.Windows.Forms.Application.Run(form);
        picture.Image?.Dispose();
    }
}
