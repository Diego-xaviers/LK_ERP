# Fila duravel de multas. Nao depende da rede nem inicia o jogo.
function New-LkEstado {
    [pscustomobject]@{ versao = 2; jobId = $null; assinatura = ''; emServico = $false;
        fined = $null; pendentes = @() }
}

function Save-LkEstado($estado, [string]$arquivo) {
    $temp = $arquivo + '.tmp'
    $json = $estado | ConvertTo-Json -Depth 8 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $stream = [IO.File]::Open($temp, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    if ([IO.File]::Exists($arquivo)) { [IO.File]::Replace($temp, $arquivo, $arquivo + '.bak') }
    else { [IO.File]::Move($temp, $arquivo) }
}

function Update-LkMultas($estado, $payload) {
    # Hora inicial do job permanece igual ao reabrir o agente durante a mesma carga.
    $assinatura = '{0}|{1}|{2}|{3}|{4}' -f $payload.jogo, $payload.inicioJob,
        $payload.cargaNome, $payload.cidadeOrigem, $payload.cidadeDestino
    $mudou = $false
    if ($payload.emServico -and (-not $estado.emServico -or $estado.assinatura -ne $assinatura -or -not $estado.jobId)) {
        $estado.jobId = [guid]::NewGuid().ToString()
        $estado.assinatura = $assinatura
        $estado.fined = $null # primeira leitura estabelece baseline, nao cobra multa antiga
        $mudou = $true
    }
    if (($payload.emServico -or $estado.emServico) -and $estado.fined -ne $null -and [bool]$estado.fined -ne [bool]$payload.fined -and $estado.jobId -and $payload.fineAccumulator -gt 0) {
        $estado.pendentes = @($estado.pendentes) + [pscustomobject]@{
            id = [guid]::NewGuid().ToString(); agenteJobId = $estado.jobId;
            valor = [decimal]$payload.fineAccumulator; ocorridoEm = [DateTime]::UtcNow.ToString('o')
        }
        $mudou = $true
    }
    if ($estado.fined -eq $null -or [bool]$estado.fined -ne [bool]$payload.fined -or $estado.emServico -ne $payload.emServico) { $mudou = $true }
    $estado.fined = [bool]$payload.fined
    $estado.emServico = [bool]$payload.emServico
    return $mudou
}

function Confirm-LkMultas($estado, $ids) {
    $antes = @($estado.pendentes).Count
    $estado.pendentes = @($estado.pendentes | Where-Object { $_.id -notin @($ids) })
    return $antes -ne @($estado.pendentes).Count
}
