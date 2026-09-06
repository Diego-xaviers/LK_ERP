# ============================================================
#  LK Transportes - Agente de Telemetria (Launcher)
#  Janela grafica com status em tempo real.
#  Nao requer instalacao: roda direto no PowerShell do Windows.
# ============================================================

param([int] $Intervalo = 2)

$ErrorActionPreference = 'Stop'

# ---- Leitura da memoria compartilhada (scs-sdk-plugin) -----
$MAPA_NOME    = 'Local\SCSTelemetry'
$MAPA_TAM     = 21620
$FILE_MAP_READ = 0x0004

if (-not ('LkMem' -as [type])) {
    Add-Type -Namespace '' -Name 'LkMem' -MemberDefinition @'
[DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
public static extern IntPtr OpenFileMapping(uint dwDesiredAccess, bool bInheritHandle, string lpName);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern IntPtr MapViewOfFile(IntPtr hFileMappingObject, uint dwDesiredAccess,
    uint dwFileOffsetHigh, uint dwFileOffsetLow, UIntPtr dwNumberOfBytesToMap);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool UnmapViewOfFile(IntPtr lpBaseAddress);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool CloseHandle(IntPtr hObject);
'@
}

function Read-Telemetria {
    $handle = [LkMem]::OpenFileMapping($FILE_MAP_READ, $false, $MAPA_NOME)
    if ($handle -eq [IntPtr]::Zero) { return $null }
    try {
        $view = [LkMem]::MapViewOfFile($handle, $FILE_MAP_READ, 0, 0, [UIntPtr]::new($MAPA_TAM))
        if ($view -eq [IntPtr]::Zero) { return $null }
        try {
            $bytes = New-Object byte[] $MAPA_TAM
            [System.Runtime.InteropServices.Marshal]::Copy($view, $bytes, 0, $MAPA_TAM)
            return $bytes
        } finally { [void][LkMem]::UnmapViewOfFile($view) }
    } finally { [void][LkMem]::CloseHandle($handle) }
}

function Get-Texto([byte[]] $b, [int] $off, [int] $tam = 64) {
    $fim = $off
    while ($fim -lt ($off + $tam) -and $b[$fim] -ne 0) { $fim++ }
    if ($fim -eq $off) { return $null }
    return [System.Text.Encoding]::UTF8.GetString($b, $off, $fim - $off)
}
function Get-Float([byte[]] $b, [int] $off) { [Math]::Round([BitConverter]::ToSingle($b, $off), 3) }
function Get-FloatBruto([byte[]] $b, [int] $off) { [BitConverter]::ToSingle($b, $off) }
function Get-Bool([byte[]] $b, [int] $off) { $b[$off] -ne 0 }
function Get-UInt([byte[]] $b, [int] $off) { [BitConverter]::ToUInt32($b, $off) }
function Get-Int([byte[]] $b, [int] $off) { [BitConverter]::ToInt32($b, $off) }

function ConvertTo-Payload([byte[]] $b) {
    $pct = { param($off) [Math]::Round((Get-FloatBruto $b $off) * 100, 2) }
    [ordered]@{
        jogoAtivo             = (Get-Bool $b 0)
        pausado               = (Get-Bool $b 4)
        tempoJogoMin          = (Get-UInt $b 64)
        velocidadeKmh         = [Math]::Round((Get-Float $b 948) * 3.6, 1)
        rpm                   = (Get-Float $b 952)
        marcha                = (Get-Int $b 504)
        combustivelL          = (Get-Float $b 1000)
        combustivelCapacidadeL= (Get-Float $b 704)
        consumoMedioLKm       = (Get-Float $b 1004)
        odometroKm            = (Get-Float $b 1056)
        desgasteMotorPct      = (& $pct 1036)
        desgasteCambioPct     = (& $pct 1040)
        desgasteCabinePct     = (& $pct 1044)
        desgasteChassiPct     = (& $pct 1048)
        desgasteRodasPct      = (& $pct 1052)
        desgasteCargaPct      = (& $pct 1468)
        posX                  = [Math]::Round([BitConverter]::ToDouble($b, 2200), 2)
        posY                  = [Math]::Round([BitConverter]::ToDouble($b, 2208), 2)
        posZ                  = [Math]::Round([BitConverter]::ToDouble($b, 2216), 2)
        pilotoAutomatico      = (Get-Bool $b 1589)
        pilotoAutomaticoKmh   = [Math]::Round((Get-Float $b 988) * 3.6, 1)
        estacionamentoAutomatico = (Get-Bool $b 1613)
        emServico             = (Get-Bool $b 4300)
        abastecendo           = (Get-Bool $b 4308)
        entregaFeita          = (Get-Bool $b 4303)
        cargaNome             = (Get-Texto $b 2620)
        cargaMassaKg          = (Get-Float $b 748)
        cidadeOrigem          = (Get-Texto $b 3004)
        cidadeOrigemId        = (Get-Texto $b 2940)
        cidadeDestino         = (Get-Texto $b 2748)
        cidadeDestinoId       = (Get-Texto $b 2684)
        empresaOrigem         = (Get-Texto $b 3132)
        empresaDestino        = (Get-Texto $b 2876)
        distanciaPlanejadaKm  = (Get-UInt $b 100)
        placaCaminhao         = (Get-Texto $b 3212)
        modeloCaminhao        = (Get-Texto $b 2492)
        jogo                  = (Get-UInt $b 52)
    }
}

# ---- Config ----
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$arquivoConfig = Join-Path $raiz 'lk-telemetria.json'
if (-not (Test-Path $arquivoConfig)) {
    [System.Windows.Forms.MessageBox]::Show(
        "Arquivo lk-telemetria.json nao encontrado.`nBaixe o pacote novamente pelo painel em Telemetria.",
        "LK Transportes", 0, 16) | Out-Null
    exit 1
}
$cfg = Get-Content $arquivoConfig -Raw | ConvertFrom-Json

# ---- WinForms ----
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$COR_BG       = [System.Drawing.Color]::FromArgb(14, 14, 14)
$COR_CARD     = [System.Drawing.Color]::FromArgb(22, 22, 22)
$COR_BORDA    = [System.Drawing.Color]::FromArgb(40, 40, 40)
$COR_VERDE    = [System.Drawing.Color]::FromArgb(46, 204, 113)
$COR_AMARELO  = [System.Drawing.Color]::FromArgb(245, 166, 35)
$COR_VERMELHO = [System.Drawing.Color]::FromArgb(231, 76, 60)
$COR_TEXTO    = [System.Drawing.Color]::White
$COR_SUB      = [System.Drawing.Color]::FromArgb(136, 136, 136)

$form = New-Object System.Windows.Forms.Form
$form.Text            = "LK Transportes — Agente de Telemetria"
$form.Size            = New-Object System.Drawing.Size(420, 340)
$form.MinimumSize     = $form.Size
$form.MaximumSize     = $form.Size
$form.StartPosition   = "CenterScreen"
$form.BackColor       = $COR_BG
$form.ForeColor       = $COR_TEXTO
$form.FormBorderStyle = "FixedSingle"
$form.MaximizeBox     = $false

# Topo: logo
$pnlTopo = New-Object System.Windows.Forms.Panel
$pnlTopo.Size      = New-Object System.Drawing.Size(420, 70)
$pnlTopo.Location  = New-Object System.Drawing.Point(0, 0)
$pnlTopo.BackColor = $COR_CARD

$lblLK = New-Object System.Windows.Forms.Label
$lblLK.Text      = "LK"
$lblLK.Font      = New-Object System.Drawing.Font("Segoe UI", 28, [System.Drawing.FontStyle]::Bold)
$lblLK.ForeColor = $COR_VERDE
$lblLK.Location  = New-Object System.Drawing.Point(20, 12)
$lblLK.Size      = New-Object System.Drawing.Size(60, 46)

$lblTitulo = New-Object System.Windows.Forms.Label
$lblTitulo.Text      = "Transportes"
$lblTitulo.Font      = New-Object System.Drawing.Font("Segoe UI", 13, [System.Drawing.FontStyle]::Regular)
$lblTitulo.ForeColor = $COR_TEXTO
$lblTitulo.Location  = New-Object System.Drawing.Point(82, 14)
$lblTitulo.Size      = New-Object System.Drawing.Size(200, 26)

$lblSub = New-Object System.Windows.Forms.Label
$lblSub.Text      = "Agente de Telemetria"
$lblSub.Font      = New-Object System.Drawing.Font("Segoe UI", 8)
$lblSub.ForeColor = $COR_SUB
$lblSub.Location  = New-Object System.Drawing.Point(83, 40)
$lblSub.Size      = New-Object System.Drawing.Size(200, 18)

$lblMotorista = New-Object System.Windows.Forms.Label
$lblMotorista.Text      = $cfg.motorista
$lblMotorista.Font      = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
$lblMotorista.ForeColor = $COR_SUB
$lblMotorista.TextAlign = "MiddleRight"
$lblMotorista.Location  = New-Object System.Drawing.Point(220, 25)
$lblMotorista.Size      = New-Object System.Drawing.Size(180, 20)

$pnlTopo.Controls.AddRange(@($lblLK, $lblTitulo, $lblSub, $lblMotorista))

# Status card
$pnlStatus = New-Object System.Windows.Forms.Panel
$pnlStatus.Size      = New-Object System.Drawing.Size(380, 60)
$pnlStatus.Location  = New-Object System.Drawing.Point(20, 84)
$pnlStatus.BackColor = $COR_CARD
$pnlStatus.BorderStyle = "FixedSingle"

$lblDotLabel = New-Object System.Windows.Forms.Label
$lblDotLabel.Text      = "●"
$lblDotLabel.Font      = New-Object System.Drawing.Font("Segoe UI", 14)
$lblDotLabel.ForeColor = $COR_SUB
$lblDotLabel.Location  = New-Object System.Drawing.Point(12, 18)
$lblDotLabel.Size      = New-Object System.Drawing.Size(24, 24)

$lblStatusTxt = New-Object System.Windows.Forms.Label
$lblStatusTxt.Text      = "Iniciando..."
$lblStatusTxt.Font      = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$lblStatusTxt.ForeColor = $COR_SUB
$lblStatusTxt.Location  = New-Object System.Drawing.Point(40, 8)
$lblStatusTxt.Size      = New-Object System.Drawing.Size(320, 22)

$lblStatusSub = New-Object System.Windows.Forms.Label
$lblStatusSub.Text      = ""
$lblStatusSub.Font      = New-Object System.Drawing.Font("Segoe UI", 8)
$lblStatusSub.ForeColor = $COR_SUB
$lblStatusSub.Location  = New-Object System.Drawing.Point(40, 32)
$lblStatusSub.Size      = New-Object System.Drawing.Size(320, 18)

$pnlStatus.Controls.AddRange(@($lblDotLabel, $lblStatusTxt, $lblStatusSub))

# Log area
$lblLogTitle = New-Object System.Windows.Forms.Label
$lblLogTitle.Text      = "ATIVIDADE RECENTE"
$lblLogTitle.Font      = New-Object System.Drawing.Font("Segoe UI", 7, [System.Drawing.FontStyle]::Bold)
$lblLogTitle.ForeColor = $COR_SUB
$lblLogTitle.Location  = New-Object System.Drawing.Point(20, 158)
$lblLogTitle.Size      = New-Object System.Drawing.Size(200, 14)

$lstLog = New-Object System.Windows.Forms.ListBox
$lstLog.Size            = New-Object System.Drawing.Size(380, 108)
$lstLog.Location        = New-Object System.Drawing.Point(20, 174)
$lstLog.BackColor       = $COR_CARD
$lstLog.ForeColor       = $COR_SUB
$lstLog.Font            = New-Object System.Drawing.Font("Consolas", 8)
$lstLog.BorderStyle     = "FixedSingle"
$lstLog.SelectionMode   = "None"

# Rodape
$lblRodape = New-Object System.Windows.Forms.Label
$lblRodape.Text      = "Deixe esta janela aberta enquanto joga"
$lblRodape.Font      = New-Object System.Drawing.Font("Segoe UI", 7)
$lblRodape.ForeColor = $COR_SUB
$lblRodape.TextAlign = "MiddleCenter"
$lblRodape.Location  = New-Object System.Drawing.Point(0, 294)
$lblRodape.Size      = New-Object System.Drawing.Size(420, 18)

$form.Controls.AddRange(@($pnlTopo, $pnlStatus, $lblLogTitle, $lstLog, $lblRodape))

# Tray icon
$tray = New-Object System.Windows.Forms.NotifyIcon
$tray.Icon    = [System.Drawing.SystemIcons]::Application
$tray.Visible = $true
$tray.Text    = "LK Transportes - Agente"

$menuTray = New-Object System.Windows.Forms.ContextMenuStrip
$itemAbrir = $menuTray.Items.Add("Abrir")
$itemSair  = $menuTray.Items.Add("Encerrar agente")
$tray.ContextMenuStrip = $menuTray

$itemAbrir.add_Click({ $form.Show(); $form.WindowState = "Normal"; $form.Activate() })
$itemSair.add_Click({ $tray.Visible = $false; $form.Close() })
$tray.add_DoubleClick({ $form.Show(); $form.WindowState = "Normal"; $form.Activate() })

$form.add_Resize({
    if ($form.WindowState -eq "Minimized") { $form.Hide() }
})

# ---- Helpers de UI ----
$logEntradas = New-Object System.Collections.Generic.Queue[string]

function Add-Log([string] $msg, [string] $cor = "normal") {
    $hora = Get-Date -Format "HH:mm:ss"
    $entrada = "[$hora] $msg"
    $logEntradas.Enqueue($entrada)
    while ($logEntradas.Count -gt 8) { $logEntradas.Dequeue() | Out-Null }
    $lstLog.Items.Clear()
    foreach ($e in $logEntradas) { $lstLog.Items.Add($e) | Out-Null }
    if ($lstLog.Items.Count -gt 0) { $lstLog.TopIndex = $lstLog.Items.Count - 1 }
}

function Set-Status([string] $texto, [string] $sub, [System.Drawing.Color] $cor) {
    $lblStatusTxt.Text      = $texto
    $lblStatusTxt.ForeColor = $cor
    $lblDotLabel.ForeColor  = $cor
    $lblStatusSub.Text      = $sub
}

# ---- Loop de telemetria via Timer ----
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = $Intervalo * 1000

$timer.add_Tick({
    try {
        $bytes = Read-Telemetria
        if ($null -eq $bytes) {
            Set-Status "Aguardando o jogo abrir..." "" $COR_SUB
            return
        }
        $p = ConvertTo-Payload $bytes
        if (-not $p.jogoAtivo) {
            Set-Status "Jogo aberto, aguardando partida..." "" $COR_SUB
            return
        }

        $corpo = [System.Text.Encoding]::UTF8.GetBytes(($p | ConvertTo-Json -Depth 5 -Compress))
        $resp  = Invoke-RestMethod -Method Post -Uri "$($cfg.servidor)/telemetria/ping" `
                     -Headers @{ 'X-Telemetria-Token' = $cfg.token } `
                     -ContentType 'application/json; charset=utf-8' -Body $corpo -TimeoutSec 8

        if ($resp.viagem) {
            $subTxt = "$($p.velocidadeKmh) km/h"
            if ($p.cargaNome) { $subTxt += " · $($p.cargaNome)" }
            Set-Status "Em viagem #$($resp.viagem)" $subTxt $COR_VERDE
        } else {
            Set-Status "Conectado — sem viagem em aberto" "" $COR_AMARELO
        }

        # Notificacoes de acoes automaticas
        if ($resp.acao -like "VIAGEM_CRIADA:*") {
            $num = ($resp.acao -split ":")[1]
            Add-Log "Viagem #$num criada automaticamente! Acesse o painel." "verde"
            $tray.ShowBalloonTip(6000, "LK Transportes", "Viagem #$num iniciada automaticamente.", [System.Windows.Forms.ToolTipIcon]::Info)
        } elseif ($resp.acao -like "ENTREGA_CONCLUIDA:*") {
            $num = ($resp.acao -split ":")[1]
            Add-Log "Viagem #$num concluida! Va ao escritorio entregar os documentos." "verde"
            $tray.ShowBalloonTip(8000, "LK Transportes", "Entrega registrada! Viagem #$num concluida.", [System.Windows.Forms.ToolTipIcon]::Info)
        }

    } catch {
        $msg = $_.Exception.Message
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 401) {
            Set-Status "Token invalido" "Baixe o pacote novamente pelo painel" $COR_VERMELHO
            Add-Log "ERRO: Token invalido."
        } else {
            Set-Status "Erro de conexao" $msg $COR_VERMELHO
            Add-Log "ERRO: $msg"
        }
    }
})

$form.add_Shown({ $timer.Start(); Add-Log "Agente iniciado." })
$form.add_FormClosed({ $timer.Stop(); $tray.Visible = $false })

[System.Windows.Forms.Application]::Run($form)
