# =====================================================================
#  LK Transportes - Agente de Telemetria
#  Le a memoria compartilhada publicada pelo plugin scs-sdk-plugin
#  (Local\SCSTelemetry) e envia o estado do caminhao para o painel.
#
#  Nao precisa instalar nada: roda no PowerShell que ja vem no Windows.
#  Configuracao fica em lk-telemetria.json, ao lado deste arquivo.
# =====================================================================
param(
    # Mostra uma leitura unica e sai - util pra conferir se o plugin funciona.
    [switch] $Diagnostico,
    # Intervalo entre envios, em segundos.
    [int] $Intervalo = 2
)

$ErrorActionPreference = 'Stop'
$MAPA_NOME  = 'Local\SCSTelemetry'
$MAPA_TAM   = 21620      # tamanho do scsTelemetryMap_t
$FILE_MAP_READ = 0x0004

# ---------------------------------------------------------------------
# Acesso a memoria compartilhada via kernel32
# ---------------------------------------------------------------------
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
    if ($handle -eq [IntPtr]::Zero) { return $null }   # jogo fechado ou plugin ausente
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

# ---------------------------------------------------------------------
# Leitura dos campos - offsets conforme scs-telemetry-common.hpp
# ---------------------------------------------------------------------
function Get-Texto([byte[]] $b, [int] $off, [int] $tam = 64) {
    $fim = $off
    while ($fim -lt ($off + $tam) -and $b[$fim] -ne 0) { $fim++ }
    if ($fim -eq $off) { return $null }
    return [System.Text.Encoding]::UTF8.GetString($b, $off, $fim - $off)
}
function Get-Float([byte[]] $b, [int] $off) { [Math]::Round([BitConverter]::ToSingle($b, $off), 3) }
function Get-FloatBruto([byte[]] $b, [int] $off) { [BitConverter]::ToSingle($b, $off) }
function Get-Double([byte[]] $b, [int] $off) { [Math]::Round([BitConverter]::ToDouble($b, $off), 2) }
function Get-Bool([byte[]] $b, [int] $off) { $b[$off] -ne 0 }
function Get-UInt([byte[]] $b, [int] $off) { [BitConverter]::ToUInt32($b, $off) }
function Get-Int([byte[]] $b, [int] $off) { [BitConverter]::ToInt32($b, $off) }

function ConvertTo-Payload([byte[]] $b) {
    # Desgaste vem 0..1 no jogo; o painel trabalha em porcentagem.
    # Le sem arredondar antes de multiplicar, senao 0,0123 vira 1,2 % em vez de 1,23 %.
    $pct = { param($off) [Math]::Round((Get-FloatBruto $b $off) * 100, 2) }

    [ordered]@{
        jogoAtivo   = (Get-Bool $b 0)
        pausado     = (Get-Bool $b 4)
        tempoJogoMin = (Get-UInt $b 64)

        velocidadeKmh = [Math]::Round((Get-Float $b 948) * 3.6, 1)
        rpm           = (Get-Float $b 952)
        marcha        = (Get-Int $b 504)

        combustivelL         = (Get-Float $b 1000)
        combustivelCapacidadeL = (Get-Float $b 704)
        consumoMedioLKm      = (Get-Float $b 1004)

        odometroKm = (Get-Float $b 1056)

        desgasteMotorPct  = (& $pct 1036)
        desgasteCambioPct = (& $pct 1040)
        desgasteCabinePct = (& $pct 1044)
        desgasteChassiPct = (& $pct 1048)
        desgasteRodasPct  = (& $pct 1052)
        desgasteCargaPct  = (& $pct 1468)

        posX = (Get-Double $b 2200)
        posY = (Get-Double $b 2208)
        posZ = (Get-Double $b 2216)

        pilotoAutomatico    = (Get-Bool $b 1589)
        pilotoAutomaticoKmh = [Math]::Round((Get-Float $b 988) * 3.6, 1)
        estacionamentoAutomatico = (Get-Bool $b 1613)

        emServico     = (Get-Bool $b 4300)
        abastecendo   = (Get-Bool $b 4308)
        entregaFeita  = (Get-Bool $b 4303)

        cargaNome      = (Get-Texto $b 2620)
        cargaMassaKg   = (Get-Float $b 748)
        cidadeOrigem   = (Get-Texto $b 3004)
        cidadeOrigemId = (Get-Texto $b 2940)
        cidadeDestino  = (Get-Texto $b 2748)
        cidadeDestinoId = (Get-Texto $b 2684)
        empresaOrigem  = (Get-Texto $b 3132)
        empresaDestino = (Get-Texto $b 2876)
        distanciaPlanejadaKm = (Get-UInt $b 100)

        placaCaminhao = (Get-Texto $b 3212)
        modeloCaminhao = (Get-Texto $b 2492)
        jogo = (Get-UInt $b 52)   # 1 = ETS2, 2 = ATS
    }
}

# ---------------------------------------------------------------------
# Configuracao
# ---------------------------------------------------------------------
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$arquivoConfig = Join-Path $raiz 'lk-telemetria.json'

if (-not (Test-Path $arquivoConfig)) {
    Write-Host "Arquivo lk-telemetria.json nao encontrado." -ForegroundColor Red
    Write-Host "Baixe o pacote novamente pelo painel, em Telemetria." -ForegroundColor Red
    Read-Host "Enter para sair"; exit 1
}
$cfg = Get-Content $arquivoConfig -Raw | ConvertFrom-Json

# ---------------------------------------------------------------------
# Modo diagnostico: uma leitura e sai
# ---------------------------------------------------------------------
if ($Diagnostico) {
    $b = Read-Telemetria
    if ($null -eq $b) {
        Write-Host "Memoria '$MAPA_NOME' nao encontrada." -ForegroundColor Yellow
        Write-Host "Abra o Euro Truck Simulator 2 e entre em uma partida antes de testar."
    } else {
        ConvertTo-Payload $b | Format-List
    }
    Read-Host "Enter para sair"; exit 0
}

# ---------------------------------------------------------------------
# Loop principal
# ---------------------------------------------------------------------
Write-Host ""
Write-Host "  LK Transportes - Agente de Telemetria" -ForegroundColor Cyan
Write-Host "  Servidor: $($cfg.servidor)"
Write-Host "  Motorista: $($cfg.motorista)"
Write-Host "  (deixe esta janela aberta enquanto joga - Ctrl+C encerra)"
Write-Host ""

$ultimoEstado = ''
function Write-Estado([string] $texto, [string] $cor) {
    if ($texto -ne $script:ultimoEstado) {
        Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $texto) -ForegroundColor $cor
        $script:ultimoEstado = $texto
    }
}

while ($true) {
    try {
        $bytes = Read-Telemetria
        if ($null -eq $bytes) {
            Write-Estado 'Aguardando o Euro Truck Simulator 2 abrir...' 'DarkGray'
        } else {
            $payload = ConvertTo-Payload $bytes
            if (-not $payload.jogoAtivo) {
                Write-Estado 'Jogo aberto, aguardando entrar na partida...' 'DarkGray'
            } else {
                $json = $payload | ConvertTo-Json -Depth 5 -Compress
                $corpo = [System.Text.Encoding]::UTF8.GetBytes($json)
                $resp = Invoke-RestMethod -Method Post -Uri "$($cfg.servidor)/telemetria/ping" `
                    -Headers @{ 'X-Telemetria-Token' = $cfg.token } `
                    -ContentType 'application/json; charset=utf-8' -Body $corpo -TimeoutSec 10

                if ($resp.viagem) {
                    Write-Estado ("Enviando - viagem #{0} - {1} km/h" -f $resp.viagem, $payload.velocidadeKmh) 'Green'
                } else {
                    Write-Estado 'Conectado, mas sem viagem em andamento no painel.' 'Yellow'
                }
            }
        }
    } catch {
        $msg = $_.Exception.Message
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 401) {
            Write-Estado 'Token invalido. Baixe o pacote novamente pelo painel.' 'Red'
        } else {
            Write-Estado "Falha ao falar com o servidor: $msg" 'Red'
        }
    }
    Start-Sleep -Seconds $Intervalo
}
