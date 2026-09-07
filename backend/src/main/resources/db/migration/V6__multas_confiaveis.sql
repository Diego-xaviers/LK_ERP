ALTER TABLE eventos_viagem ADD COLUMN chave_externa VARCHAR(100) UNIQUE;
ALTER TABLE eventos_viagem ADD COLUMN ajuste_vtlog BOOLEAN DEFAULT FALSE;
ALTER TABLE viagens ADD COLUMN agente_job_id UUID UNIQUE;
ALTER TABLE viagens ADD COLUMN multas_vtlog NUMERIC(14,2);
ALTER TABLE viagens ADD COLUMN pendencia_multas VARCHAR(600);
