-- Telemetria: estado da entrega e ação pendente para notificação no painel
ALTER TABLE telemetria_sessao ADD COLUMN IF NOT EXISTS entrega_feita  BOOLEAN;
ALTER TABLE telemetria_sessao ADD COLUMN IF NOT EXISTS acao_pendente  VARCHAR(60);
