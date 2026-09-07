# Multas: captura, recibos e conferência

## Comportamento

- O pacote continua abrindo pelo `LK-Telemetria.bat`, com os dois PS1 de apoio.
- A leitura local ocorre a cada 100 ms. Cada mudança do sinal `fined` gera um recibo UUID; valores iguais são eventos diferentes. A primeira leitura estabelece uma referência e não cobra uma multa antiga.
- A fila é salva atomicamente em `%LOCALAPPDATA%/LK-Transportes/telemetria/<conta>/multas.json`, com backup do arquivo anterior. Não contém o token. Uma única instância por conta pode utilizar a fila.
- O envio HTTP roda em paralelo, a cada dois segundos. Somente os IDs confirmados pelo servidor saem da fila. Com o jogo fechado, o endpoint de recibos não atualiza a presença online.
- O servidor usa chave única e bloqueio transacional por motorista. Recibos repetidos não duplicam despesas. Uma multa de uma carga anterior mantém seu vínculo, mesmo depois de começar outra viagem.
- O identificador local da carga é vinculado à viagem pelo ping. Um recibo sem vínculo fica na fila; nunca é atribuído automaticamente a outra carga.
- O bot importa `expense_fines`, inclusive quando vier como texto. Ausência ou `finance_logging=false` não equivalem a zero. O bot reconsulta as últimas 50 mensagens a cada minuto e não considera todo erro 409 uma entrega duplicada.
- A entrega é vinculada pelo job VTLog já conhecido ou por uma única viagem de mesma rota/carga e início dentro da janela da entrega (tolerância de cinco minutos). Ambiguidade é recusada e registrada nos logs do bot.
- O ajuste financeiro é `max(total VTLog - multas registradas, 0)`. Recibos atrasados reduzem o ajuste, preservando o total. O ajuste não representa uma infração individual e não desconta pontos de CNH.
- A viagem com agente fica pendente para acerto até chegar o total definitivo. Divergência, total ausente ou atualização após pagamento exigem conferência da gestão. A justificativa fica registrada em uma ocorrência. O pagamento anterior e a comissão por km não são recalculados.
- Snapshots ao vivo não geram despesas. Sua assinatura HMAC-SHA256 deve corresponder ao corpo bruto em `X-VTLog-Signature`, como hexadecimal ou `sha256=<hex>`. O segredo é `VTLOG_WEBHOOK_SECRET`, diferente de `VTLOG_SECRET` usado pelo bot. Sem configuração, o webhook responde 503; assinatura inválida responde 401.

## Outras despesas automáticas

- **Combustível:** o agente mostra cada abastecimento durante a viagem; no fechamento,
  `expense_fuel`, `fuel_used` e `price_fuel` criam o custo definitivo do combustível.
- **Pedágio:** o bot consulta `/v1/jobs/{id}/events` e importa cada evento `toll` pelo ID
  único do VTLog. Um lançamento antigo do agente com mesmo valor e horário é adotado,
  não duplicado.
- **Oficina:** despesas de cabine, chassi, motor, câmbio, rodas e carreta, inclusive
  desgaste, são somadas numa manutenção automática detalhada.
- Reenvios são idempotentes. Uma correção recebida depois de um acerto não altera o
  pagamento histórico silenciosamente: ela abre uma pendência para a gestão.

## Implantação coordenada

1. Configurar no serviço LK_ERP o segredo de assinatura do webhook existente no VTLog, em `VTLOG_WEBHOOK_SECRET`. Não criar uma chave aleatória somente no Railway: os dois lados precisam usar o mesmo segredo.
2. Publicar backend com a migração aditiva V6. Publicar frontend com a conferência de multas.
3. Publicar `discord-bot`, incluindo `multas.js`. O backend aceita o bot anterior, mas marca ausência de total como pendência.
4. Motoristas devem substituir o pacote antigo pelo ZIP completo. A captura antiga de multas por ping foi removida para impedir conflito com os recibos.
5. Verificar um webhook assinado e uma entrega real. Conferir o total do VTLog, as multas e o ajuste no histórico. Não lançar cobranças sintéticas em produção.

## Testes

- `backend/mvnw.cmd -o test`: suíte completa e migração V5→V6, sem reaplicar colunas.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/src/test/agente/multas.test.ps1`: decodificação dos bytes reais, sinais alternados, valores iguais, persistência, confirmação parcial, reinício, troca de carga e multas fora de serviço. Executado em Windows PowerShell 5.1.
- `node --test discord-bot/multas.test.js`: valores numéricos/textuais, ausentes e inválidos.
- `npm run build` em `frontend`: TypeScript e empacotamento.

## Limites conhecidos

- Duas multas ocorridas entre duas leituras podem ultrapassar a capacidade do sinal booleano da DLL. O total definitivo do VTLog recupera a diferença, mas não reconstrói cada infração individual.
- Sem agente durante a carga inteira, ou sem vínculo inicial com o servidor, recibos locais podem permanecer pendentes. Não são descartados nem associados por aproximação. Conferir o vínculo com a gestão.
- O bot depende das mensagens de entrega e de sua API. A recuperação automática de histórico cobre 50 mensagens; entregas anteriores a essa janela precisam ser reprocessadas explicitamente.
- Migração testada em H2 no modo PostgreSQL; o fluxo completo com DLL, assinatura VTLog e dados reais requer validação após a implantação.
- Totais aceitos na conferência manual não removem eventos nem corrigem um pagamento já efetuado. O gestor deve revisar os registros antes de confirmar.
