-- Comissão deixa de ser percentual sobre o frete e passa a ser valor por km
-- rodado — o mesmo km que a telemetria confirma e que a conferência já exige.
--
-- Motivo: percentual sobre o frete bruto paga por carga cara, não por trabalho.
-- Uma viagem de 25 t a R$ 150/t rendia R$ 450 de comissão; a mesma viagem paga
-- por km rende ~R$ 175. Além disso, quem não roda com o agente ligado não tem
-- km confirmado — e, sem km, não há o que pagar.

ALTER TABLE caixa DROP COLUMN percentual_comissao_padrao;
ALTER TABLE caixa ADD COLUMN valor_km_padrao NUMERIC(8,3) NOT NULL DEFAULT 0.350;

ALTER TABLE usuarios DROP COLUMN percentual_comissao;
ALTER TABLE usuarios ADD COLUMN valor_km_comissao NUMERIC(8,3);

ALTER TABLE pagamentos DROP COLUMN percentual_aplicado;
ALTER TABLE pagamentos ADD COLUMN valor_km_aplicado NUMERIC(8,3) NOT NULL DEFAULT 0.350;
ALTER TABLE pagamentos ADD COLUMN base_km NUMERIC(12,1) NOT NULL DEFAULT 0;
