# LK Transportes — Bootstrap do Agente de Telemetria
# Compila o LK-Transportes.exe na primeira execução e o lança.
# Requer PowerShell 5.1+ e .NET Framework 4.6+ (já incluído no Windows 10/11).

param([switch]$Recompilar)

$raiz   = Split-Path -Parent $MyInvocation.MyCommand.Path
$config = Join-Path $raiz "lk-telemetria.json"
$exe    = Join-Path $raiz "LK-Transportes.exe"

if (-not (Test-Path $config)) {
    [System.Windows.Forms.MessageBox]::Show(
        "Arquivo lk-telemetria.json não encontrado.`nReinstale o agente pelo painel LK.",
        "LK Transportes", "OK", "Error") | Out-Null
    exit 1
}

# Lê configuração
$cfg = Get-Content $config -Raw | ConvertFrom-Json
$servidor  = $cfg.servidor
$token     = $cfg.token
$motorista = $cfg.motorista

# Compila o EXE se não existir ou se forçado
if ($Recompilar -or -not (Test-Path $exe)) {
    Write-Host "Compilando LK-Transportes.exe..." -ForegroundColor Cyan

    # Fonte C# do aplicativo WinForms (WindowsApplication = sem console)
    $fonte = @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.IO;
using Newtonsoft.Json;

static class Programa {
    [STAThread]
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new JanelaPrincipal());
    }
}

class JanelaPrincipal : Form {
    // Configuração injetada pelo bootstrap
    public static string Servidor = "";
    public static string Token    = "";
    public static string Motorista = "";

    // Controles
    NotifyIcon tray;
    Label lblStatus, lblVelocidade, lblCombustivel;
    Panel painelDot;
    ListBox log;
    System.Windows.Forms.Timer timer;
    HttpClient http;
    bool online = false;

    static readonly Color COR_BG    = Color.FromArgb(14,  14,  14);
    static readonly Color COR_CARD  = Color.FromArgb(22,  22,  22);
    static readonly Color COR_VERDE = Color.FromArgb(46,  204, 113);
    static readonly Color COR_AMARELO = Color.FromArgb(245, 166, 35);
    static readonly Color COR_BORDA = Color.FromArgb(38,  38,  38);
    static readonly Color COR_TEXTO = Color.FromArgb(220, 220, 220);
    static readonly Color COR_SUTIL = Color.FromArgb(100, 100, 100);

    public JanelaPrincipal() {
        // Lê config do diretório do EXE
        try {
            string cfgPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "lk-telemetria.json");
            dynamic cfg = JsonConvert.DeserializeObject(File.ReadAllText(cfgPath));
            Servidor  = cfg.servidor;
            Token     = cfg.token;
            Motorista = cfg.motorista;
        } catch {
            MessageBox.Show("lk-telemetria.json não encontrado.", "LK Transportes");
            Application.Exit(); return;
        }

        http = new HttpClient();
        http.DefaultRequestHeaders.Add("X-Telemetria-Token", Token);

        ConfigurarJanela();
        ConfigurarTray();
        ConfigurarTimer();
    }

    void ConfigurarJanela() {
        this.Text            = "LK Transportes · Agente";
        this.Size            = new Size(360, 500);
        this.FormBorderStyle = FormBorderStyle.FixedSingle;
        this.MaximizeBox     = false;
        this.BackColor       = COR_BG;
        this.ForeColor       = COR_TEXTO;
        this.StartPosition   = FormStartPosition.Manual;
        this.Location        = new Point(Screen.PrimaryScreen.WorkingArea.Width - 380, 40);
        this.ShowInTaskbar   = true;
        this.Icon            = CriarIcone();
        this.FormClosing    += (s, e) => { e.Cancel = true; this.Hide(); };

        // Logo LK
        var logo = new Panel {
            Size      = new Size(360, 90),
            Location  = new Point(0, 0),
            BackColor = Color.FromArgb(18, 80, 45),
        };
        logo.Paint += (s, e) => {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            // Texto LK grande
            using var fLK = new Font("Segoe UI", 36, FontStyle.Bold);
            using var b1  = new SolidBrush(Color.FromArgb(245, 200, 40));
            g.DrawString("LK", fLK, b1, 18, 16);
            // Subtítulo
            using var fSub = new Font("Segoe UI", 9);
            using var b2   = new SolidBrush(Color.FromArgb(160, 220, 160));
            g.DrawString("TRANSPORTES", fSub, b2, 100, 26);
            g.DrawString("Agente de Telemetria", fSub, b2, 100, 46);
        };
        this.Controls.Add(logo);

        // Card de status
        var cardStatus = CriarCard(new Rectangle(16, 104, 328, 64));
        painelDot = new Panel { Size = new Size(12, 12), BackColor = COR_SUTIL };
        painelDot.Location = new Point(16, 26);
        ArredondarPanel(painelDot, 6);
        cardStatus.Controls.Add(painelDot);

        lblStatus = new Label {
            Text      = "Desconectado",
            Location  = new Point(38, 22),
            Size      = new Size(240, 22),
            Font      = new Font("Segoe UI", 10, FontStyle.Bold),
            ForeColor = COR_SUTIL,
            BackColor = Color.Transparent,
        };
        cardStatus.Controls.Add(lblStatus);

        var lblMotorista = new Label {
            Text      = Motorista,
            Location  = new Point(38, 40),
            Size      = new Size(260, 18),
            Font      = new Font("Segoe UI", 8),
            ForeColor = COR_SUTIL,
            BackColor = Color.Transparent,
        };
        cardStatus.Controls.Add(lblMotorista);

        // Card de telemetria ao vivo
        var cardTele = CriarCard(new Rectangle(16, 180, 328, 80));
        lblVelocidade = new Label {
            Text = "— km/h", Location = new Point(16, 14),
            Size = new Size(140, 50), Font = new Font("Segoe UI", 22, FontStyle.Bold),
            ForeColor = COR_VERDE, BackColor = Color.Transparent
        };
        cardTele.Controls.Add(lblVelocidade);
        lblCombustivel = new Label {
            Text = "Combustível: —", Location = new Point(170, 20),
            Size = new Size(140, 40), Font = new Font("Segoe UI", 8.5f),
            ForeColor = COR_TEXTO, BackColor = Color.Transparent
        };
        cardTele.Controls.Add(lblCombustivel);

        // Log de atividade
        var lblLog = new Label {
            Text = "Atividade", Location = new Point(16, 276),
            Font = new Font("Segoe UI", 8, FontStyle.Bold),
            ForeColor = COR_SUTIL, BackColor = COR_BG,
            AutoSize = true
        };
        this.Controls.Add(lblLog);

        log = new ListBox {
            Location         = new Point(16, 296),
            Size             = new Size(328, 150),
            BackColor        = COR_CARD,
            ForeColor        = COR_TEXTO,
            BorderStyle      = BorderStyle.None,
            Font             = new Font("Consolas", 7.5f),
            SelectionMode    = SelectionMode.None,
            HorizontalScrollbar = false,
        };
        this.Controls.Add(log);

        AdicionarLog("Agente iniciado. Abra o ETS2 para conectar.");
    }

    Panel CriarCard(Rectangle r) {
        var p = new Panel {
            Location  = new Point(r.X, r.Y),
            Size      = new Size(r.Width, r.Height),
            BackColor = COR_CARD,
        };
        p.Paint += (s, e) => {
            using var pen = new Pen(COR_BORDA, 1);
            e.Graphics.DrawRectangle(pen, 0, 0, p.Width - 1, p.Height - 1);
        };
        this.Controls.Add(p);
        return p;
    }

    void ArredondarPanel(Panel p, int r) {
        var path = new System.Drawing.Drawing2D.GraphicsPath();
        path.AddEllipse(0, 0, r * 2, r * 2);
        p.Region = new Region(path);
    }

    void ConfigurarTray() {
        tray = new NotifyIcon {
            Icon    = CriarIcone(),
            Visible = true,
            Text    = "LK Transportes · Agente",
        };
        var menu = new ContextMenuStrip();
        menu.Items.Add("Abrir painel").Click    += (s, e) => { this.Show(); this.WindowState = FormWindowState.Normal; };
        menu.Items.Add("Fechar agente").Click   += (s, e) => { tray.Visible = false; Application.Exit(); };
        tray.ContextMenuStrip = menu;
        tray.DoubleClick     += (s, e) => { this.Show(); this.WindowState = FormWindowState.Normal; };
    }

    Icon CriarIcone() {
        var bmp = new Bitmap(32, 32);
        using var g = Graphics.FromImage(bmp);
        g.Clear(Color.FromArgb(18, 80, 45));
        using var f = new Font("Segoe UI", 12, FontStyle.Bold);
        using var b = new SolidBrush(Color.FromArgb(245, 200, 40));
        g.DrawString("LK", f, b, 2, 6);
        return Icon.FromHandle(bmp.GetHicon());
    }

    void ConfigurarTimer() {
        timer = new System.Windows.Forms.Timer { Interval = 2000 };
        timer.Tick += async (s, e) => await EnviarPing();
        timer.Start();
    }

    async Task EnviarPing() {
        try {
            var tele = LerTelemetriaEts2();
            if (tele == null) {
                if (online) { online = false; AtualizarStatus(false, null); }
                return;
            }

            var json = System.Text.Json.JsonSerializer.Serialize(tele);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            var resp = await http.PostAsync(Servidor + "/telemetria/ping", content);

            if (resp.IsSuccessStatusCode) {
                var body = await resp.Content.ReadAsStringAsync();
                dynamic r = JsonConvert.DeserializeObject(body);
                online = true;
                AtualizarStatus(true, tele);

                string acao = r?.acao;
                if (!string.IsNullOrEmpty(acao)) TratarAcao(acao);
            }
        } catch {
            if (online) { online = false; AtualizarStatus(false, null); }
        }
    }

    dynamic LerTelemetriaEts2() {
        try {
            using var mmf = System.IO.MemoryMappedFiles.MemoryMappedFile.OpenExisting(@"Local\SCSTelemetry");
            using var acc = mmf.CreateViewAccessor(0, 0, System.IO.MemoryMappedFiles.MemoryMappedFileAccess.Read);
            int gameState = acc.ReadInt32(0);
            if (gameState == 0) return null;

            float speed   = acc.ReadSingle(32);
            float fuel    = acc.ReadSingle(108);
            float fuelCap = acc.ReadSingle(112);
            float rpm     = acc.ReadSingle(24);
            float odometer = acc.ReadSingle(480);
            bool  emServico = acc.ReadBoolean(420);
            bool  entregaFeita = acc.ReadBoolean(424);
            bool  abastecendo  = acc.ReadBoolean(432);
            float danoMotor = acc.ReadSingle(240);
            float danoEngrenagem = acc.ReadSingle(244);
            float danoCabine = acc.ReadSingle(248);
            float danoChassi = acc.ReadSingle(252);
            float danoRoda   = acc.ReadSingle(256);
            float danoCarga  = acc.ReadSingle(260);
            float posX = acc.ReadSingle(48);
            float posY = acc.ReadSingle(52);
            float posZ = acc.ReadSingle(56);

            return new {
                velocidadeKmh = (double)(speed * 3.6f),
                rpm           = (double)rpm,
                combustivelL  = (double)fuel,
                combustivelCapacidadeL = (double)fuelCap,
                odometroKm    = (double)(odometer / 1000f),
                emServico     = emServico,
                entregaFeita  = entregaFeita,
                abastecendo   = abastecendo,
                desgasteMotorPct    = (double)(danoMotor * 100),
                desgasteCambioPct   = (double)(danoEngrenagem * 100),
                desgasteCabinePct   = (double)(danoCabine * 100),
                desgasteChassiPct   = (double)(danoChassi * 100),
                desgasteRodasPct    = (double)(danoRoda * 100),
                desgasteCargaPct    = (double)(danoCarga * 100),
                posX = (double)posX,
                posY = (double)posY,
                posZ = (double)posZ,
            };
        } catch {
            return null;
        }
    }

    void AtualizarStatus(bool conectado, dynamic tele) {
        if (this.InvokeRequired) {
            this.Invoke(new Action(() => AtualizarStatus(conectado, tele)));
            return;
        }

        if (conectado) {
            painelDot.BackColor = COR_VERDE;
            lblStatus.ForeColor = COR_VERDE;
            lblStatus.Text      = "Conectado ao painel";

            if (tele != null) {
                double kmh = tele.velocidadeKmh;
                lblVelocidade.Text  = $"{kmh:0} km/h";
                double fuel = tele.combustivelL;
                double cap  = tele.combustivelCapacidadeL;
                lblCombustivel.Text = cap > 0
                    ? $"Combustível\n{fuel:0} L  ({fuel/cap*100:0}%)"
                    : $"Combustível\n{fuel:0} L";
            }
        } else {
            painelDot.BackColor = Color.FromArgb(80, 80, 80);
            lblStatus.ForeColor = COR_SUTIL;
            lblStatus.Text      = "ETS2 não encontrado";
            lblVelocidade.Text  = "— km/h";
            lblCombustivel.Text = "Combustível: —";
        }
    }

    void TratarAcao(string acao) {
        if (acao.StartsWith("VIAGEM_CRIADA:")) {
            string num = acao.Split(':')[1];
            AdicionarLog($"[✓] Viagem #{num} criada automaticamente");
            tray.ShowBalloonTip(5000, "LK Transportes", $"Viagem #{num} criada! Documentos gerados.", ToolTipIcon.Info);
        } else if (acao.StartsWith("ENTREGA_CONCLUIDA:")) {
            string num = acao.Split(':')[1];
            AdicionarLog($"[✓] Entrega da viagem #{num} concluída");
            tray.ShowBalloonTip(5000, "LK Transportes", $"Entrega #{num} registrada! Acesse o painel.", ToolTipIcon.Info);
        }
    }

    void AdicionarLog(string msg) {
        if (this.InvokeRequired) { this.Invoke(new Action(() => AdicionarLog(msg))); return; }
        string hora = DateTime.Now.ToString("HH:mm:ss");
        log.Items.Insert(0, $"{hora}  {msg}");
        if (log.Items.Count > 100) log.Items.RemoveAt(log.Items.Count - 1);
    }

    protected override void OnLoad(EventArgs e) {
        base.OnLoad(e);
        this.Show();
        this.Activate();
    }
}
"@

    # Dependências necessárias
    $refs = @(
        "System.Windows.Forms",
        "System.Drawing",
        "System.Net.Http",
        "Newtonsoft.Json"
    )

    # Verifica se Newtonsoft.Json está disponível (pode precisar de download)
    $newtonsoftPath = "$raiz\Newtonsoft.Json.dll"
    if (-not (Test-Path $newtonsoftPath)) {
        Write-Host "Baixando Newtonsoft.Json..." -ForegroundColor Yellow
        $url = "https://www.nuget.org/api/v2/package/Newtonsoft.Json/13.0.3"
        $zip = "$env:TEMP\newtonsoft.zip"
        Invoke-WebRequest -Uri $url -OutFile $zip
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, "$env:TEMP\newtonsoft")
        Copy-Item "$env:TEMP\newtonsoft\lib\net45\Newtonsoft.Json.dll" -Destination $newtonsoftPath
        Remove-Item $zip, "$env:TEMP\newtonsoft" -Recurse -Force
    }

    $refs += $newtonsoftPath

    try {
        Add-Type -TypeDefinition $fonte -OutputAssembly $exe -OutputType WindowsApplication `
                 -ReferencedAssemblies $refs -Language CSharp
        Write-Host "Compilado com sucesso: $exe" -ForegroundColor Green
    } catch {
        Write-Host "Erro na compilação: $_" -ForegroundColor Red
        Write-Host "Usando modo PowerShell como fallback..." -ForegroundColor Yellow
        IniciarModoPowerShell -servidor $servidor -token $token -motorista $motorista
        exit
    }
}

# Copia o Newtonsoft.Json.dll para junto do EXE (necessário em tempo de execução)
$newtonsoftPath = "$raiz\Newtonsoft.Json.dll"
if (Test-Path $newtonsoftPath) {
    Copy-Item $newtonsoftPath -Destination (Split-Path $exe) -Force 2>$null
}

# Lança o EXE compilado
Start-Process -FilePath $exe -WorkingDirectory $raiz
