import test from 'node:test';
import assert from 'node:assert/strict';
import { despesasDoJob } from './despesas.js';

test('extrai combustível, oficina e pedágios sem duplicar totais', () => {
  const r = despesasDoJob({ finance_logging: true, expense_fuel: '187', fuel_used: 152,
    price_fuel: '1.233', expense_truck_wear_engine: 7, expense_truck_wear_transmission: 6,
    expense_truck_wear_wheels: 17, expense_trailer_wear_wheels: 13 },
    [{ id: 91, type: 'toll', date: '1788815978931', num1: '12.00' },
     { id: 92, type: 'fine', date: '1788815979000', num1: '80' }]);
  assert.equal(r.total_combustivel, 187);
  assert.equal(r.total_manutencao, 43);
  assert.equal(r.pedagios.length, 1);
  assert.deepEqual(r.pedagios[0], { id: '91', valor: 12, ocorrido_epoch_ms: 1788815978931,
    detalhe: 'Pedágio confirmado pelo histórico do VTLog' });
});

test('financeiro desligado não inventa zero, mas mantém pedágio observado', () => {
  const r = despesasDoJob({ finance_logging: false, expense_fuel: 999 },
    [{ id: 5, type: 'toll', date: 1000, num1: 8 }]);
  assert.equal(r.total_combustivel, null);
  assert.equal(r.total_manutencao, null);
  assert.equal(r.pedagios.length, 1);
});

test('valor financeiro inválido é recusado', () => {
  assert.throws(() => despesasDoJob({ finance_logging: true, expense_fuel: '-1' }, []));
});

test('campo ausente não é convertido em manutenção zero', () => {
  const r = despesasDoJob({ finance_logging: true, expense_fuel: 0 }, []);
  assert.equal(r.total_manutencao, null);
});
