# LK Transportes — Bootstrap do Agente
# Compila LK-Transportes.exe na primeira execucao (sem janela visivel)
# e o lanca. Requer .NET Framework 4.6+ (nativo no Windows 10/11).

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe  = Join-Path $raiz "LK-Transportes.exe"

$fonte = @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.IO.MemoryMappedFiles;
using System.Net.Http;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;

static class App {
    [STAThread]
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new Launcher());
    }
}

class Launcher : Form {

    // ── Configuracao (lida do JSON) ───────────────────────────────────────
    string servidor, token, nomeMotorista;

    // ── Estado vivo ───────────────────────────────────────────────────────
    bool   online;
    double speed, fuel, fuelCap, danoMotor, danoCambio, danoCabine, danoChassi, danoRodas;
    bool   emServico;
    int?   tripNum;
    string pendingAction = null;

    // ── Log ───────────────────────────────────────────────────────────────
    readonly System.Collections.Generic.Queue<string> logLines =
        new System.Collections.Generic.Queue<string>();

    // ── UI ────────────────────────────────────────────────────────────────
    NotifyIcon tray;
    System.Windows.Forms.Timer ticker;
    HttpClient http;
    int pulse;
    bool dragging;
    Point dragOffset;

    // ── Cores ─────────────────────────────────────────────────────────────
    static readonly Color C_BG      = Color.FromArgb( 9,  11,  17);
    static readonly Color C_HDR1    = Color.FromArgb(10,  38,  22);
    static readonly Color C_HDR2    = Color.FromArgb( 7,   9,  14);
    static readonly Color C_CARD    = Color.FromArgb(13,  16,  24);
    static readonly Color C_BORDER  = Color.FromArgb(22,  28,  42);
    static readonly Color C_GREEN   = Color.FromArgb(46, 204, 113);
    static readonly Color C_YELLOW  = Color.FromArgb(245,197,  24);
    static readonly Color C_RED     = Color.FromArgb(231,  76,  60);
    static readonly Color C_ORANGE  = Color.FromArgb(230, 126,  34);
    static readonly Color C_TEXT    = Color.FromArgb(205, 215, 235);
    static readonly Color C_DIM     = Color.FromArgb( 80,  95, 120);

    // ── Fontes ────────────────────────────────────────────────────────────
    Font fLogo, fBig, fMed, fSm, fTiny;

    // ── Dimensoes ─────────────────────────────────────────────────────────
    const int W = 460, H = 570;
    const int HDR  = 120;   // altura do header
    const int STS  = 44;    // altura da status bar
    const int PADX = 20;

    public Launcher() {
        // Config
        try {
            string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "lk-telemetria.json");
            string json = File.ReadAllText(path);
            servidor      = JsonField(json, "servidor");
            token         = JsonField(json, "token");
            nomeMotorista = JsonField(json, "motorista");
        } catch {
            MessageBox.Show(
                "Arquivo lk-telemetria.json nao encontrado.\n\nBaixe novamente pelo painel LK.",
                "LK Transportes", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Environment.Exit(1);
        }

        http = new HttpClient { Timeout = TimeSpan.FromSeconds(4) };
        http.DefaultRequestHeaders.Add("X-Telemetria-Token", token);

        // Fontes
        fLogo  = new Font("Segoe UI", 30, FontStyle.Bold);
        fBig   = new Font("Segoe UI", 46, FontStyle.Bold);
        fMed   = new Font("Segoe UI", 10, FontStyle.Regular);
        fSm    = new Font("Segoe UI",  8, FontStyle.Regular);
        fTiny  = new Font("Segoe UI",  7, FontStyle.Regular);

        // Janela
        Text            = "LK Transportes";
        ClientSize      = new Size(W, H);
        FormBorderStyle = FormBorderStyle.None;
        StartPosition   = FormStartPosition.Manual;
        DoubleBuffered  = true;
        BackColor       = C_BG;
        Icon            = MakeIcon();

        Screen scr = Screen.PrimaryScreen;
        Location = new Point(scr.WorkingArea.Right - W - 20, 20);

        UpdateRegion();

        // Arrastar janela
        MouseDown += (s, e) => { if (e.Button == MouseButtons.Left) { dragging = true; dragOffset = e.Location; } };
        MouseMove += (s, e) => { if (dragging) Location = new Point(Cursor.Position.X - dragOffset.X, Cursor.Position.Y - dragOffset.Y); };
        MouseUp   += (s, e) =>   dragging = false;

        // Botao fechar (canto superior direito — clique direto na area)
        MouseClick += (s, e) => {
            if (e.Button == MouseButtons.Left && new Rectangle(W - 36, 8, 26, 26).Contains(e.Location))
                Hide();
        };

        // Tray
        var menu = new ContextMenuStrip();
        menu.Items.Add("Abrir painel").Click   += (s, e) => { Show(); Activate(); };
        menu.Items.Add("Encerrar agente").Click += (s, e) => { tray.Visible = false; Application.Exit(); };
        tray = new NotifyIcon { Icon = Icon, Text = "LK Transportes · Agente", Visible = true, ContextMenuStrip = menu };
        tray.DoubleClick += (s, e) => { Show(); Activate(); };

        FormClosing += (s, e) => { if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };

        // Timer principal
        ticker = new System.Windows.Forms.Timer { Interval = 2000 };
        ticker.Tick += async (s, e) => { pulse = (pulse + 1) % 10; await EnviarPing(); Invalidate(); };
        ticker.Start();

        AddLog("Agente iniciado. Abra o ETS2 para conectar.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Regiao arredondada
    // ─────────────────────────────────────────────────────────────────────

    void UpdateRegion() {
        var p = new GraphicsPath();
        int r = 12;
        p.AddArc(0, 0, r*2, r*2, 180, 90);
        p.AddArc(W-r*2, 0, r*2, r*2, 270, 90);
        p.AddArc(W-r*2, H-r*2, r*2, r*2, 0, 90);
        p.AddArc(0, H-r*2, r*2, r*2, 90, 90);
        p.CloseFigure();
        Region = new Region(p);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pintura principal
    // ─────────────────────────────────────────────────────────────────────

    protected override void OnPaint(PaintEventArgs e) {
        var g = e.Graphics;
        g.SmoothingMode     = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

        g.Clear(C_BG);

        DrawHeader(g);
        DrawStatusBar(g, HDR);
        DrawMainStats(g, HDR + STS + 10);
        DrawDamageRow(g, HDR + STS + 150);
        DrawTripInfo(g, HDR + STS + 240);
        DrawLog(g, HDR + STS + 330);
        DrawFooter(g, H - 50);
    }

    // ── Header ───────────────────────────────────────────────────────────

    void DrawHeader(Graphics g) {
        var r = new Rectangle(0, 0, W, HDR);

        // Gradiente verde escuro → fundo
        using (var b = new LinearGradientBrush(new Rectangle(0, 0, W, HDR + 20), C_HDR1, C_HDR2, 110f))
            g.FillRectangle(b, r);

        // Linha borda inferior sutil
        using (var p = new Pen(Color.FromArgb(60, C_GREEN), 1))
            g.DrawLine(p, 0, HDR - 1, W, HDR - 1);

        // Decoracao: faixa vertical esquerda amarela
        using (var b = new SolidBrush(C_YELLOW))
            g.FillRectangle(b, 0, 0, 5, HDR);

        // Texto "LK"
        using (var b = new SolidBrush(C_YELLOW))
            g.DrawString("LK", fLogo, b, 18, 18);

        // Texto "TRANSPORTES"
        using (var fT = new Font("Segoe UI", 13, FontStyle.Bold))
        using (var b  = new SolidBrush(C_TEXT))
            g.DrawString("TRANSPORTES", fT, b, 20, 60);

        // Subtitulo
        using (var b = new SolidBrush(C_DIM))
            g.DrawString("Sistema de Telemetria · ETS2 / ATS", fSm, b, 21, 82);

        // Decoracao: circulo no canto direito
        using (var b = new SolidBrush(Color.FromArgb(25, C_GREEN))) {
            g.FillEllipse(b, W - 130, -40, 160, 160);
        }
        using (var b = new SolidBrush(Color.FromArgb(15, C_YELLOW))) {
            g.FillEllipse(b, W - 100, -20, 120, 120);
        }

        // Icone caminhao (desenhado)
        DrawTruckIcon(g, W - 95, 30, 80);

        // Botao fechar [×] discreto
        using (var b = new SolidBrush(Color.FromArgb(online ? 120 : 80, C_RED)))
            g.FillEllipse(b, W - 34, 10, 22, 22);
        using (var b = new SolidBrush(C_TEXT))
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
        using (var fX = new Font("Segoe UI", 9, FontStyle.Bold))
            g.DrawString("×", fX, b, new RectangleF(W - 34, 10, 22, 22), sf);
    }

    void DrawTruckIcon(Graphics g, int x, int y, int size) {
        // Cabine
        int s = size;
        using (var b = new SolidBrush(Color.FromArgb(40, C_GREEN))) {
            // Carroceria
            g.FillRoundedRect(b, x, y + s/4, (int)(s*0.55), (int)(s*0.45), 4);
            // Cabine
            g.FillRoundedRect(b, x + (int)(s*0.55), y + (int)(s*0.15), (int)(s*0.38), (int)(s*0.54), 4);
        }
        // Rodas
        using (var b = new SolidBrush(Color.FromArgb(60, C_DIM))) {
            g.FillEllipse(b, x + (int)(s*0.05), y + (int)(s*0.63), (int)(s*0.22), (int)(s*0.22));
            g.FillEllipse(b, x + (int)(s*0.38), y + (int)(s*0.63), (int)(s*0.22), (int)(s*0.22));
            g.FillEllipse(b, x + (int)(s*0.68), y + (int)(s*0.63), (int)(s*0.22), (int)(s*0.22));
        }
    }

    // ── Status bar ───────────────────────────────────────────────────────

    void DrawStatusBar(Graphics g, int y) {
        var r = new Rectangle(0, y, W, STS);
        using (var b = new SolidBrush(Color.FromArgb(180, C_CARD)))
            g.FillRectangle(b, r);

        // Dot pulsante
        int dotR = 8;
        int dotX = PADX + 6, dotY = y + STS/2 - dotR/2;
        Color dotColor = online ? C_GREEN : C_DIM;

        // Aura (pulsa)
        if (online) {
            int aura = (int)(4 + 3 * Math.Sin(pulse * Math.PI / 5.0));
            using (var b = new SolidBrush(Color.FromArgb(50, dotColor)))
                g.FillEllipse(b, dotX - aura, dotY - aura, dotR + aura*2, dotR + aura*2);
        }
        using (var b = new SolidBrush(dotColor))
            g.FillEllipse(b, dotX, dotY, dotR, dotR);

        // Status text
        string statusText = online ? "Agente conectado" : "ETS2 nao detectado";
        using (var b = new SolidBrush(online ? C_GREEN : C_DIM))
        using (var fS = new Font("Segoe UI", 9, FontStyle.Bold))
            g.DrawString(statusText, fS, b, dotX + dotR + 10, y + STS/2 - 7);

        // Nome motorista (direita)
        using (var b = new SolidBrush(C_DIM))
        using (var sf = new StringFormat { Alignment = StringAlignment.Far })
            g.DrawString(nomeMotorista, fSm, b, new RectangleF(0, y + STS/2 - 7, W - PADX, 16), sf);
    }

    // ── Stats principais ─────────────────────────────────────────────────

    void DrawMainStats(Graphics g, int y) {
        int col1 = PADX, col2 = W/2 + 10;

        // Velocidade
        DrawCard(g, col1, y, W/2 - PADX - 5, 140);
        using (var sf = new StringFormat { Alignment = StringAlignment.Center })
        using (var b  = new SolidBrush(online ? C_GREEN : C_DIM))
            g.DrawString(online ? ((int)speed).ToString() : "—", fBig, b,
                new RectangleF(col1, y + 10, W/2 - PADX - 5, 80), sf);
        using (var sf = new StringFormat { Alignment = StringAlignment.Center })
        using (var b  = new SolidBrush(C_DIM))
        using (var fU = new Font("Segoe UI", 9))
            g.DrawString("km/h", fU, b, new RectangleF(col1, y + 90, W/2 - PADX - 5, 20), sf);

        // Rotacao sutil
        if (online) {
            using (var b = new SolidBrush(C_DIM))
            using (var sf = new StringFormat { Alignment = StringAlignment.Center })
                g.DrawString("ETS2 · Online", fTiny, b,
                    new RectangleF(col1, y + 112, W/2 - PADX - 5, 16), sf);
        }

        // Combustivel
        DrawCard(g, col2, y, W - col2 - PADX, 140);

        int bx = col2 + 14, by = y + 20, bw = W - col2 - PADX - 28, bh = 18;
        double tankPct = (fuelCap > 0) ? Math.Min(1, fuel / fuelCap) : 0;

        using (var b = new SolidBrush(C_DIM))
        using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
            g.DrawString("COMBUSTIVEL", fL, b, bx, by - 14);

        // Barra gradiente
        DrawBar(g, bx, by, bw, bh, tankPct,
            tankPct < 0.15 ? C_RED : tankPct < 0.30 ? C_ORANGE : C_GREEN);

        using (var b = new SolidBrush(C_TEXT))
            g.DrawString(string.Format("{0} L  ({1}%)", (int)fuel, (int)(tankPct*100)), fSm, b, bx, by + bh + 6);

        // Odometro e emServico
        int iy = by + bh + 28;
        using (var b = new SolidBrush(C_DIM))
        using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
            g.DrawString("SITUACAO", fL, b, bx, iy - 2);
        iy += 14;
        string situ = emServico ? "Em servico" : (online ? "Sem carga" : "—");
        Color situColor = emServico ? C_GREEN : (online ? C_YELLOW : C_DIM);
        using (var b = new SolidBrush(situColor))
            g.DrawString(situ, fMed, b, bx, iy);

        if (tripNum.HasValue) {
            iy += 22;
            using (var b = new SolidBrush(C_DIM))
            using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
                g.DrawString("VIAGEM", fL, b, bx, iy);
            iy += 14;
            using (var b = new SolidBrush(C_TEXT))
            using (var fT = new Font("Segoe UI", 10, FontStyle.Bold))
                g.DrawString("#" + tripNum.Value, fT, b, bx, iy);
        }
    }

    // ── Linha de danos ───────────────────────────────────────────────────

    void DrawDamageRow(Graphics g, int y) {
        DrawCard(g, PADX, y, W - PADX*2, 78);

        using (var b = new SolidBrush(C_DIM))
        using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
            g.DrawString("ESTADO DO CAMINHAO", fL, b, PADX + 12, y + 10);

        string[] labels = { "Motor", "Cambio", "Cabine", "Chassi", "Rodas" };
        double[] vals   = { danoMotor, danoCambio, danoCabine, danoChassi, danoRodas };
        int cols = labels.Length;
        int bw = (W - PADX*2 - 24) / cols - 6;

        for (int i = 0; i < cols; i++) {
            int bx = PADX + 12 + i * (bw + 6);
            int by = y + 26;
            double pct = Math.Min(1.0, vals[i] / 100.0);
            Color c = pct > 0.25 ? C_RED : pct > 0.10 ? C_ORANGE : C_GREEN;

            using (var b = new SolidBrush(C_DIM))
            using (var sf = new StringFormat { Alignment = StringAlignment.Center })
                g.DrawString(labels[i], fTiny, b, new RectangleF(bx, by, bw, 12), sf);

            DrawBar(g, bx, by + 13, bw, 10, pct, c);

            using (var b = new SolidBrush(pct > 0.10 ? c : C_DIM))
            using (var sf = new StringFormat { Alignment = StringAlignment.Center })
                g.DrawString(string.Format("{0:F0}%", vals[i]), fTiny, b, new RectangleF(bx, by + 25, bw, 12), sf);
        }
    }

    // ── Info da viagem ───────────────────────────────────────────────────

    void DrawTripInfo(Graphics g, int y) {
        DrawCard(g, PADX, y, W - PADX*2, 72);

        using (var b = new SolidBrush(C_DIM))
        using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
            g.DrawString("VIAGEM ATIVA", fL, b, PADX + 12, y + 10);

        if (!tripNum.HasValue || !online) {
            using (var b = new SolidBrush(C_DIM))
                g.DrawString("Nenhuma viagem em andamento", fSm, b, PADX + 12, y + 28);
            using (var b = new SolidBrush(Color.FromArgb(100, C_DIM)))
                g.DrawString("Inicie uma viagem no painel LK Transportes", fTiny, b, PADX + 12, y + 46);
        } else {
            using (var b = new SolidBrush(C_GREEN))
            using (var fN = new Font("Segoe UI", 11, FontStyle.Bold))
                g.DrawString("Viagem #" + tripNum.Value + " · Em andamento", fN, b, PADX + 12, y + 26);
            using (var b = new SolidBrush(C_DIM))
                g.DrawString("Telemetria sendo registrada automaticamente", fSm, b, PADX + 12, y + 48);
        }
    }

    // ── Log ──────────────────────────────────────────────────────────────

    void DrawLog(Graphics g, int y) {
        DrawCard(g, PADX, y, W - PADX*2, 108);

        using (var b = new SolidBrush(C_DIM))
        using (var fL = new Font("Segoe UI", 7, FontStyle.Bold))
            g.DrawString("ATIVIDADE RECENTE", fL, b, PADX + 12, y + 8);

        var lines = logLines.ToArray();
        for (int i = lines.Length - 1, row = 0; i >= 0 && row < 5; i--, row++) {
            using (var b = new SolidBrush(row == 0 ? C_TEXT : C_DIM))
                g.DrawString(lines[i], fTiny, b, PADX + 12, y + 22 + row * 16);
        }
    }

    // ── Footer ───────────────────────────────────────────────────────────

    void DrawFooter(Graphics g, int y) {
        using (var p = new Pen(C_BORDER, 1))
            g.DrawLine(p, PADX, y, W - PADX, y);

        using (var b = new SolidBrush(C_DIM))
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
        using (var fF = new Font("Segoe UI", 7))
            g.DrawString("© LK Transportes · Agente v2.0 · ETS2 / ATS", fF, b,
                new RectangleF(0, y + 8, W, 20), sf);

        // Linha de acento colorido no rodape
        using (var b = new SolidBrush(C_YELLOW))
            g.FillRectangle(b, 0, H - 4, W, 4);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers de desenho
    // ─────────────────────────────────────────────────────────────────────

    void DrawCard(Graphics g, int x, int y, int w, int h) {
        using (var b = new SolidBrush(C_CARD))
            g.FillRoundedRect(b, x, y, w, h, 8);
        using (var p = new Pen(C_BORDER, 1))
            g.DrawRoundedRect(p, x, y, w, h, 8);
    }

    void DrawBar(Graphics g, int x, int y, int w, int h, double pct, Color fill) {
        using (var b = new SolidBrush(Color.FromArgb(40, fill)))
            g.FillRoundedRect(b, x, y, w, h, 4);
        int fw = Math.Max(0, (int)(w * pct));
        if (fw > 0) {
            using (var b2 = new LinearGradientBrush(
                new Rectangle(x, y, Math.Max(1, fw), h),
                Color.FromArgb(180, fill), fill, LinearGradientMode.Horizontal))
                g.FillRoundedRect(b2, x, y, fw, h, 4);
        }
    }

    Icon MakeIcon() {
        var bmp = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(bmp)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.FromArgb(11, 38, 22));
            using (var b = new SolidBrush(Color.FromArgb(245, 197, 24)))
            using (var f = new Font("Segoe UI", 13, FontStyle.Bold))
                g.DrawString("LK", f, b, 2, 6);
        }
        return Icon.FromHandle(bmp.GetHicon());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Leitura da telemetria ETS2
    // ─────────────────────────────────────────────────────────────────────

    async Task EnviarPing() {
        try {
            var t = LerEts2();
            if (t == null) { online = false; return; }

            string body = string.Format(
                "{{\"velocidadeKmh\":{0:F1},\"combustivelL\":{1:F1},\"combustivelCapacidadeL\":{2:F1}," +
                "\"desgasteMotorPct\":{3:F1},\"desgasteCambioPct\":{4:F1},\"desgasteCabinePct\":{5:F1}," +
                "\"desgasteChassiPct\":{6:F1},\"desgasteRodasPct\":{7:F1}," +
                "\"emServico\":{8},\"entregaFeita\":{9},\"abastecendo\":{10}," +
                "\"posX\":{11:F1},\"posY\":{12:F1},\"posZ\":{13:F1}}}",
                t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7],
                ((bool)(object)t[8] ? "true" : "false"),
                ((bool)(object)t[9] ? "true" : "false"),
                ((bool)(object)t[10] ? "true" : "false"),
                t[11], t[12], t[13]);

            using (var content = new StringContent(body, Encoding.UTF8, "application/json")) {
                var resp = await http.PostAsync(servidor + "/telemetria/ping", content);
                if (!resp.IsSuccessStatusCode) { online = false; return; }

                string rb = await resp.Content.ReadAsStringAsync();
                online  = true;
                speed   = (double)t[0];
                fuel    = (double)t[1]; fuelCap = (double)t[2];
                danoMotor  = (double)t[3];  danoCambio  = (double)t[4];
                danoCabine = (double)t[5];  danoChassi  = (double)t[6];
                danoRodas  = (double)t[7];
                emServico = (bool)(object)t[8];

                string viagem = JsonField(rb, "viagem");
                tripNum = viagem != "" ? (int?)int.Parse(viagem) : null;

                string acao = JsonField(rb, "acao");
                if (acao != "" && acao != pendingAction) {
                    pendingAction = acao;
                    HandleAction(acao);
                }
            }
        } catch {
            online = false;
        }
    }

    object[] LerEts2() {
        try {
            using (var mmf = MemoryMappedFile.OpenExisting(@"Local\SCSTelemetry"))
            using (var acc = mmf.CreateViewAccessor(0, 0, MemoryMappedFileAccess.Read)) {
                if (acc.ReadInt32(0) == 0) return null;
                return new object[] {
                    (double)(acc.ReadSingle(32) * 3.6f),
                    (double)acc.ReadSingle(108),
                    (double)acc.ReadSingle(112),
                    (double)(acc.ReadSingle(240) * 100),
                    (double)(acc.ReadSingle(244) * 100),
                    (double)(acc.ReadSingle(248) * 100),
                    (double)(acc.ReadSingle(252) * 100),
                    (double)(acc.ReadSingle(256) * 100),
                    acc.ReadBoolean(420),
                    acc.ReadBoolean(424),
                    acc.ReadBoolean(432),
                    (double)acc.ReadSingle(48),
                    (double)acc.ReadSingle(52),
                    (double)acc.ReadSingle(56),
                };
            }
        } catch { return null; }
    }

    void HandleAction(string acao) {
        if (acao.StartsWith("VIAGEM_CRIADA:")) {
            string n = acao.Split(':')[1];
            AddLog("[✓] Viagem #" + n + " criada e documentos gerados!");
            tray.ShowBalloonTip(6000, "LK Transportes", "Viagem #" + n + " criada automaticamente!", ToolTipIcon.Info);
        } else if (acao.StartsWith("ENTREGA_CONCLUIDA:")) {
            string n = acao.Split(':')[1];
            AddLog("[✓] Entrega da viagem #" + n + " concluida!");
            tray.ShowBalloonTip(6000, "LK Transportes", "Entrega #" + n + " registrada. Acesse o painel.", ToolTipIcon.Info);
        }
    }

    void AddLog(string msg) {
        string entry = DateTime.Now.ToString("HH:mm") + "  " + msg;
        logLines.Enqueue(entry);
        while (logLines.Count > 20) logLines.Dequeue();
        if (InvokeRequired) Invoke(new Action(Invalidate));
        else Invalidate();
    }

    // ─────────────────────────────────────────────────────────────────────
    // JSON simples (sem dependencia externa)
    // ─────────────────────────────────────────────────────────────────────

    static string JsonField(string json, string key) {
        // Tenta campo com aspas: "key": "value"
        var m = Regex.Match(json, "\"" + Regex.Escape(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        if (m.Success) return m.Groups[1].Value;
        // Tenta campo numerico: "key": 123
        m = Regex.Match(json, "\"" + Regex.Escape(key) + "\"\\s*:\\s*([0-9.]+)");
        return m.Success ? m.Groups[1].Value : "";
    }
}

// ── Extensoes GDI+ para retangulos arredondados ───────────────────────────
static class GdiExt {
    static GraphicsPath RoundPath(int x, int y, int w, int h, int r) {
        var p = new GraphicsPath();
        p.AddArc(x, y, r*2, r*2, 180, 90);
        p.AddArc(x+w-r*2, y, r*2, r*2, 270, 90);
        p.AddArc(x+w-r*2, y+h-r*2, r*2, r*2, 0, 90);
        p.AddArc(x, y+h-r*2, r*2, r*2, 90, 90);
        p.CloseFigure();
        return p;
    }
    public static void FillRoundedRect(this Graphics g, Brush b, int x, int y, int w, int h, int r) {
        if (w <= 0 || h <= 0) return;
        using (var p = RoundPath(x, y, w, h, Math.Min(r, Math.Min(w/2, h/2))))
            g.FillPath(b, p);
    }
    public static void DrawRoundedRect(this Graphics g, Pen pen, int x, int y, int w, int h, int r) {
        if (w <= 0 || h <= 0) return;
        using (var p = RoundPath(x, y, w, h, Math.Min(r, Math.Min(w/2, h/2))))
            g.DrawPath(pen, p);
    }
}
'@

# ── Compilar se necessario ────────────────────────────────────────────────

if (-not (Test-Path $exe)) {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $refs = @(
        [System.Windows.Forms.Form].Assembly.Location,
        [System.Drawing.Graphics].Assembly.Location,
        "System.Net.Http.dll"
    )
    try {
        Add-Type -TypeDefinition $fonte -OutputAssembly $exe -OutputType WindowsApplication `
                 -ReferencedAssemblies $refs -Language CSharp -ErrorAction Stop
    } catch {
        Remove-Item $exe -Force -ErrorAction SilentlyContinue
        [System.Windows.Forms.MessageBox]::Show(
            "Nao foi possivel compilar o launcher.`n`nErro: $_",
            "LK Transportes", "OK", "Error")
        exit 1
    }
}

# ── Lancar o EXE ─────────────────────────────────────────────────────────
Start-Process -FilePath $exe -WorkingDirectory $raiz
