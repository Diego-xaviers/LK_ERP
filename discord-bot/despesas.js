const MANUTENCAO = {
  expense_truck_cabin: 'cabine',
  expense_truck_chassis: 'chassi',
  expense_truck_engine: 'motor',
  expense_truck_transmission: 'câmbio',
  expense_truck_wheels: 'rodas do caminhão',
  expense_trailer_chassis: 'chassi da carreta',
  expense_trailer_body: 'carroceria da carreta',
  expense_trailer_wheels: 'rodas da carreta',
  expense_truck_wear_engine: 'desgaste do motor',
  expense_truck_wear_transmission: 'desgaste do câmbio',
  expense_truck_wear_wheels: 'desgaste das rodas',
  expense_trailer_wear_wheels: 'desgaste das rodas da carreta',
};

function numero(v, nome, limite = 999999999999.99) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0 || n > limite) throw new Error(`${nome} inválido no VTLog`);
  return n;
}

/** Converte o job completo e seus eventos públicos no resumo contábil da LK. */
export function despesasDoJob(job, eventos) {
  const financeiro = job.finance_logging !== false && job.finance_logging !== 'false';
  let totalManutencao = 0;
  let informouManutencao = false;
  const detalhes = [];
  if (financeiro) {
    for (const [campo, rotulo] of Object.entries(MANUTENCAO)) {
      if (job[campo] !== null && job[campo] !== undefined && job[campo] !== '') informouManutencao = true;
      const valor = numero(job[campo], campo) ?? 0;
      totalManutencao += valor;
      if (valor > 0) detalhes.push(`${rotulo}: ${valor.toFixed(2)}`);
    }
  }

  const lista = Array.isArray(eventos) ? eventos : (Array.isArray(eventos?.data) ? eventos.data : []);
  const pedagios = lista.filter(e => e?.type === 'toll').map(e => ({
    id: String(e.id),
    valor: numero(e.num1, 'pedágio'),
    ocorrido_epoch_ms: numero(e.date, 'data do pedágio', Number.MAX_SAFE_INTEGER),
    detalhe: 'Pedágio confirmado pelo histórico do VTLog',
  })).filter(e => e.valor > 0 && /^\d+$/.test(e.id));

  return {
    total_combustivel: financeiro ? numero(job.expense_fuel, 'combustível') : null,
    litros_combustivel: financeiro ? numero(job.fuel_used, 'litros de combustível') : null,
    preco_combustivel: financeiro ? numero(job.price_fuel, 'preço do combustível') : null,
    total_manutencao: financeiro && informouManutencao ? Math.round(totalManutencao * 100) / 100 : null,
    detalhe_manutencao: detalhes.length ? `VTLog — ${detalhes.join('; ')}` : null,
    pedagios,
  };
}
