$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../../main/resources/agente/lk-multas.ps1')
function Assert($condition, $message) { if (-not $condition) { throw $message } }
# Usa as funcoes reais de decodificacao, sem executar o loop nem ler memoria do jogo.
$source=Join-Path $PSScriptRoot '../../main/resources/agente/lk-telemetria.ps1'
$parseErrors=$null; $tokens=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$parseErrors)
Assert ($parseErrors.Count -eq 0) 'O agente precisa ser valido no Windows PowerShell 5.1.'
$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and ($n.Name -like 'Get-*' -or $n.Name -eq 'ConvertTo-Payload')},$false) | ForEach-Object { . ([scriptblock]::Create($_.Extent.Text)) }
$buffer=New-Object byte[] 21620
$buffer[4304]=1
[BitConverter]::GetBytes([long]225).CopyTo($buffer,4216)
$decodificado=ConvertTo-Payload $buffer
Assert ($decodificado.fined -and $decodificado.fineAccumulator -eq 225) 'Os bytes da DLL devem produzir o valor correto.'
$envio=@{} + $decodificado
$envio.Remove('inicioJob')
$envio.protocolo=2; $envio.multas=@()
$transporte=$envio | ConvertTo-Json -Depth 8 -Compress | ConvertFrom-Json
Assert ($transporte.protocolo -eq 2 -and $transporte.fineAccumulator -eq 225) 'Serializacao deve preservar o protocolo e valor.'
$estado = New-LkEstado
$p = @{ jogo=1; inicioJob=123; cargaNome='Soja'; cidadeOrigem='Sinop'; cidadeDestino='Cuiaba'; emServico=$true; fined=$false; fineAccumulator=0 }
[void](Update-LkMultas $estado $p)
$job = $estado.jobId
foreach ($valor in @(100,100,100)) {
    $p.fined = -not $p.fined; $p.fineAccumulator=$valor
    [void](Update-LkMultas $estado $p)
    [void](Update-LkMultas $estado $p) # leitura repetida nao e evento novo
}
Assert (@($estado.pendentes).Count -eq 3) 'Tres multas iguais devem gerar tres recibos.'
Assert ((@($estado.pendentes.id | Select-Object -Unique)).Count -eq 3) 'IDs devem ser unicos.'
$pasta = Join-Path ([IO.Path]::GetTempPath()) ('lk-multas-test-' + [guid]::NewGuid())
[IO.Directory]::CreateDirectory($pasta) | Out-Null
$arquivo=Join-Path $pasta 'fila.json'
Save-LkEstado $estado $arquivo
$restaurado=Get-Content $arquivo -Raw -Encoding UTF8 | ConvertFrom-Json
Assert (@($restaurado.pendentes).Count -eq 3) 'Reabrir deve recuperar todos os recibos.'
[void](Update-LkMultas $restaurado $p)
Assert ($restaurado.jobId -eq $job) 'Reabrir na mesma carga deve manter o vinculo.'
Assert (@($restaurado.pendentes).Count -eq 3) 'Reabrir nao deve duplicar multa.'
[void](Confirm-LkMultas $restaurado @($restaurado.pendentes[0].id))
Save-LkEstado $restaurado $arquivo
Assert (@($restaurado.pendentes).Count -eq 2) 'Somente recibo confirmado pode sair da fila.'
$p.emServico=$false; [void](Update-LkMultas $restaurado $p)
$p.fined=-not $p.fined; [void](Update-LkMultas $restaurado $p)
Assert (@($restaurado.pendentes).Count -eq 2) 'Multa fora de servico nao pertence a carga anterior.'
$p.emServico=$true; $p.inicioJob=456; [void](Update-LkMultas $restaurado $p)
Assert ($restaurado.jobId -ne $job) 'Nova carga precisa de novo vinculo.'
Assert ($restaurado.pendentes[0].agenteJobId -eq $job) 'Recibo antigo nao pode mudar de viagem.'
Write-Host 'PASS: sinais alternados, valores iguais, reenvio, disco, reinicio, troca de carga e multa fora de servico.'
