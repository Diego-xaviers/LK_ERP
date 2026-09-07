/** Ausente não significa zero. Mantém decimais como texto no transporte JSON. */
export function totalMultas(job) {
  if (job.finance_logging === false || job.finance_logging === 'false') return null;
  const value = job.expense_fines;
  if (value === null || value === undefined || value === '') return null;
  const text = String(value).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(text) || Number(text) > 999999999999.99)
    throw new Error('VTLog informou um total de multas inválido');
  return text;
}
