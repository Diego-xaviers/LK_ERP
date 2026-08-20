export interface Documento {
  id: string;
  tipo: 'NF' | 'CTE' | 'MDFE';
  numero: number;
  serie: string;
  chaveInterna: string;
  geradoEm: string;
  motorista: string;
  caminhao: string;
  placaCaminhao: string;
  carreta?: string;
  placaCarreta?: string;
  remetente: string;
  destinatario: string;
  origem: string;
  destino: string;
  carga: string;
  pesoKg: number;
  valorCarga?: number;
  valorFrete?: number;
  numeroViagem: number;
}

export interface Evento {
  id: string;
  tipo: 'ABASTECIMENTO' | 'MANUTENCAO' | 'PEDAGIO' | 'MULTA' | 'OCORRENCIA';
  descricao: string;
  valor?: number;
  ocorridoEm: string;
  observacao?: string;
  posto?: string;
  litros?: number;
  valorLitro?: number;
  temAssinatura: boolean;
}

export interface Viagem {
  id: string;
  numero: number;
  origem: string;
  destino: string;
  empresaRemetente: string;
  empresaDestinataria: string;
  carga: string;
  pesoKg: number;
  valorCarga?: number;
  valorFrete?: number;
  motorista: string;
  caminhao: string;
  placaCaminhao: string;
  carreta?: string;
  placaCarreta?: string;
  status: 'CRIADA' | 'EM_ANDAMENTO' | 'CONCLUIDA';
  criadaEm: string;
  iniciadaEm?: string;
  finalizadaEm?: string;
  observacaoFinal?: string;
  houveAvaria?: boolean;
  conferencia?: 'APROVADA' | 'RETIDA' | 'LIBERADA';
  motivosConferencia?: string;
  liberadaParaPagamento: boolean;
  liberadaPor?: string;
  liberadaEm?: string;
  observacaoLiberacao?: string;
  demandaNumero?: number;
  demandaId?: string;
  totalDespesas: number;
  eventos: Evento[];
  documentos: Documento[];
}

export interface Caminhao {
  id: string; placa: string; marca: string; modelo: string;
  identificacaoInterna?: string; status: string;
  /** Nulo = caminhão da empresa, disponível a todos. */
  dono?: { id: string; nome: string };
}
export interface Carreta { id: string; placa: string; tipo: string; identificacaoInterna?: string; }
export interface Posto { id: string; nome: string; cidade: string; estado: string; ativo: boolean; }
export interface Oficina { id: string; nome: string; cidade: string; estado: string; ativa: boolean; }
export interface EmpresaParceira { id: string; nome: string; segmento?: string; cidade: string; estado: string; }
export interface Aviso { id: string; titulo: string; mensagem: string; tipo: 'INFORMATIVO' | 'ALERTA' | 'EVENTO'; fixado: boolean; publicadoEm: string; }
/**
 * Carga fechada com um cliente e distribuída entre os motoristas.
 * A tarifa daqui é que define o frete da viagem — o motorista não digita valor.
 */
export interface Demanda {
  id: string;
  numero: number;
  origem: string;
  destino: string;
  empresaRemetente: string;
  empresaDestinataria: string;
  carga: string;
  quantidadeTotalKg: number;
  quantidadeEntregueKg: number;
  saldoKg: number;
  saldoDisponivelKg: number;
  reservadoKg: number;
  percentualConcluido: number;
  prazoEntrega?: string;
  atrasada: boolean;
  caminhoesPermitidos: { id: string; descricao: string; placa: string }[];
  tiposReboquePermitidos: string[];
  fretePorTonelada: number;
  valorCargaPorTonelada?: number;
  status: 'ABERTA' | 'CONCLUIDA' | 'CANCELADA';
  aceitaNovaViagem: boolean;
  criadaEm: string;
  concluidaEm?: string;
  observacoes?: string;
}

/** Ficha do motorista. É daqui que a CNH puxa nome, foto e assinatura. */
export interface Perfil {
  nomeCompleto?: string;
  dataNascimento?: string;
  cpf?: string;
  rg?: string;
  orgaoEmissor?: string;
  ufEmissor?: string;
  nomeMae?: string;
  nomePai?: string;
  naturalidadeCidade?: string;
  naturalidadeUf?: string;
  fotoBase64?: string;
  assinaturaBase64?: string;
  telefone?: string;
  endereco?: string;
  cidade?: string;
  estado?: string;
  cep?: string;
  apelido?: string;
  steamId?: string;
  discord?: string;
  sobre?: string;
  prontoParaCnh: boolean;
}

/**
 * Viagem como aparece no mural da empresa — todo mundo vê.
 * Sem eventos, documentos nem observação: isso é do motorista.
 */
export interface ViagemResumo {
  id: string;
  numero: number;
  motorista: string;
  caminhao: string;
  placaCaminhao: string;
  origem: string;
  destino: string;
  carga: string;
  pesoKg: number;
  valorFrete?: number;
  totalDespesas: number;
  status: 'CRIADA' | 'EM_ANDAMENTO' | 'CONCLUIDA';
  conferencia?: 'APROVADA' | 'RETIDA' | 'LIBERADA';
  criadaEm: string;
  finalizadaEm?: string;
}

/** Estado ao vivo que o agente de telemetria publica. */
export interface TelemetriaAtual {
  online: boolean;
  atualizadoEm: string;
  velocidadeKmh?: number;
  rpm?: number;
  marcha?: number;
  combustivelL?: number;
  combustivelCapacidadeL?: number;
  odometroKm?: number;
  danoMotorPct?: number;
  danoCambioPct?: number;
  danoCabinePct?: number;
  danoChassiPct?: number;
  danoRodasPct?: number;
  danoCargaPct?: number;
  pilotoAutomatico?: boolean;
  pausado?: boolean;
  emServico?: boolean;
  cargaNome?: string;
  cargaMassaKg?: number;
  cidadeOrigem?: string;
  cidadeDestino?: string;
  empresaOrigem?: string;
  empresaDestino?: string;
  distanciaPlanejadaKm?: number;
  placaCaminhao?: string;
  modeloCaminhao?: string;
}

/** O que a telemetria apurou de uma viagem — dado observado, não declarado. */
export interface TelemetriaViagem {
  odometroInicialKm?: number;
  odometroAtualKm?: number;
  combustivelInicialL?: number;
  combustivelAtualL?: number;
  litrosAbastecidos?: number;
  danoInicialPct?: number;
  danoAtualPct?: number;
  usouPilotoAutomatico: boolean;
  usouEstacionamentoAutomatico: boolean;
  saltos: number;
  divergencias?: string;
}

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  papel: 'MOTORISTA' | 'GESTOR';
  statusAcesso: 'PENDENTE' | 'APROVADO' | 'BLOQUEADO';
  /** Comissão própria. Nulo = usa o padrão da empresa. */
  percentualComissao?: number;
  /** Créditos disponíveis na loja. */
  saldoCarteira?: number;
}
