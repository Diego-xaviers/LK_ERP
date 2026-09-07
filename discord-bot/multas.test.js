import { test } from 'node:test';
import assert from 'node:assert/strict';
import { totalMultas } from './multas.js';
test('total aceita numeros e strings sem transformar ausencia em zero', () => {
  assert.equal(totalMultas({expense_fines: 600}), '600');
  assert.equal(totalMultas({expense_fines: '600.25'}), '600.25');
  assert.equal(totalMultas({expense_fines: 0}), '0');
  assert.equal(totalMultas({}), null);
  for (const v of [-1, 'NaN', 'abc', '1000000000000']) assert.throws(() => totalMultas({expense_fines:v}));
});
