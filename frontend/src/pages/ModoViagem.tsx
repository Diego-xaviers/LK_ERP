import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { Viagem, Posto, Oficina } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import { useUsuario } from '../auth';
import SignaturePad from '../components/SignaturePad';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import './ModoViagem.css';

type TipoEvento = 'abastecimento' | 'manutencao' | 'pedagio' | 'multa' | 'ocorrencia';

const ACOES: { tipo: TipoEvento; label: string; icon: 'fuel' | 'wrench' | 'cone' | 'siren' | 'alertCircle' }[] = [
  { tipo: 'abastecimento', label: 'Abastecer', icon: 'fuel' },
  { tipo: 'manutencao', label: 'Manutenção', icon: 'wrench' },
  { tipo: 'pedagio', label: 'Pedágio', icon: 'cone' },
  { tipo: 'multa', label: 'Multa', icon: 'siren' },
  { tipo: 'ocorrencia', label: 'Ocorrência', icon: 'alertCircle' },
];

const MARCADOR: Record<string, string> = {
  ABASTECIMENTO: 'abastecimento', MANUTENCAO: 'manutencao',
  PEDAGIO: 'pedagio', MULTA: 'multa', OCORRENCIA: 'ocorrencia',
};

const brl = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
const hora = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

export default function ModoViagem() {
  const usuario = useUsuario();
  const { dados: viagem, carregando, erro, recarregar } =
    useApi<Viagem | null>(`/viagens/ativa/${usuario.id}`);

  const [aberto, setAberto] = useState<TipoEvento | null>(null);
  const [finalizando, setFinalizando] = useState(false);
  const [concluida, setConcluida] = useState<Viagem | null>(null);
  const [entrega, setEntrega] = useState<{ observacaoFinal: string; houveAvaria: boolean } | null>(null);
  const [erroEntrega, setErroEntrega] = useState<string | null>(null);

  if (carregando) return <Carregando texto="Buscando sua viagem..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  // Acabou de chegar: mostra o resultado e o caminho para emendar a próxima
  // viagem da mesma demanda, em vez de jogar direto no histórico.
  if (concluida) return <ViagemConcluida viagem={concluida} />;

  // A conferência contra a telemetria acontece de verdade no servidor durante o
  // finalizar — as etapas aqui só dão o ritmo do que já está sendo feito.
  const overlayEntrega = entrega && (
    <Processo
      etapas={['Registrando a entrega', 'Conferindo com a telemetria', 'Fechando o romaneio']}
      sucesso="Entrega registrada."
      trabalho={() => api.post<Viagem>(`/viagens/${viagem!.id}/finalizar`, entrega)}
      aoConcluir={(v) => { setEntrega(null); setConcluida(v); }}
      aoFalhar={(m) => { setEntrega(null); setErroEntrega(m); }}
    />
  );

  if (!viagem) {
    return (
      <Vazio
        titulo="Nenhuma viagem em andamento"
        descricao="Entre numa demanda na Logística para começar."
        acao={<Link className="btn" to="/logistica" style={{ marginTop: 12, textDecoration: 'none' }}>Ver demandas abertas</Link>}
      />
    );
  }

  /**
   * Viagem criada mas ainda não iniciada: antes esta tela ficava vazia e quem
   * pegava carga na Logística não tinha para onde ir. Agora ela mostra a viagem
   * e o botão que falta.
   */
  if (viagem.status === 'CRIADA') {
    return <ViagemAIniciar viagem={viagem} aoIniciar={recarregar} />;
  }


  // O modal só coleta; quem fecha a viagem é o overlay de etapas logo abaixo.
  async function finalizar(observacaoFinal: string, houveAvaria: boolean) {
    setFinalizando(false);
    setEntrega({ observacaoFinal, houveAvaria });
  }

  return (
    <div className="viagem">
      <section className="viagem__hero">
        <div className="viagem__hero-top">
          <span className="viagem__numero">Viagem #{viagem.numero}</span>
          <span className="viagem__status"><span className="viagem__status-dot" />Em andamento</span>
        </div>

        <div className="viagem__rota">
          <span>{viagem.origem}</span>
          <Icon name="arrowRight" size={19} />
          <span>{viagem.destino}</span>
        </div>

        <dl className="viagem__meta">
          <div><dt>Carga</dt><dd>{viagem.carga} — {brl(viagem.pesoKg)} kg</dd></div>
          <div><dt>Caminhão</dt><dd>{viagem.caminhao} · {viagem.placaCaminhao}</dd></div>
          <div><dt>Carreta</dt><dd>{viagem.carreta ? `${viagem.carreta} — ${viagem.placaCarreta}` : '—'}</dd></div>
        </dl>
      </section>

      <section className="viagem__acoes">
        {ACOES.map((a) => (
          <button key={a.tipo} className="acao" onClick={() => setAberto(a.tipo)}>
            <span className="acao__icone"><Icon name={a.icon} size={22} strokeWidth={1.5} /></span>
            {a.label}
          </button>
        ))}
      </section>

      <section className="viagem__timeline-card">
        <header className="viagem__timeline-head">
          <h2>Eventos da viagem</h2>
          <span className="viagem__total">
            Despesas: <strong>R$ {brl(viagem.totalDespesas ?? 0)}</strong>
          </span>
        </header>

        {viagem.eventos.length === 0 ? (
          <p className="viagem__vazio">
            Nenhum evento ainda. Use os botões acima conforme a viagem acontecer.
          </p>
        ) : (
          <ol className="timeline">
            {viagem.eventos.map((e) => (
              <li className="timeline__item" key={e.id}>
                <span className={'timeline__marker timeline__marker--' + MARCADOR[e.tipo]} />
                <span className="timeline__hora">{hora(e.ocorridoEm)}</span>
                <span className="timeline__desc">{e.descricao}</span>
                {e.valor != null && <span className="timeline__valor">R$ {brl(e.valor)}</span>}
              </li>
            ))}
          </ol>
        )}
      </section>

      {erroEntrega && <div className="modal__erro">{erroEntrega}</div>}

      <button className="viagem__finalizar"
              onClick={() => { setErroEntrega(null); setFinalizando(true); }}>
        <Icon name="flag" size={16} /> Finalizar viagem
      </button>

      {aberto && (
        <ModalEvento
          tipo={aberto}
          viagemId={viagem.id}
          onFechar={() => setAberto(null)}
          onRegistrado={() => { setAberto(null); recarregar(); }}
        />
      )}

      {finalizando && (
        <ModalFinalizar onFechar={() => setFinalizando(false)} onConfirmar={finalizar} numero={viagem.numero} />
      )}

      {overlayEntrega}
    </div>
  );
}

/* ---------------- Modal de registro de evento ---------------- */
function ModalEvento({ tipo, viagemId, onFechar, onRegistrado }: {
  tipo: TipoEvento; viagemId: string; onFechar: () => void; onRegistrado: () => void;
}) {
  const postos = useApi<Posto[]>(tipo === 'abastecimento' ? '/postos' : null);
  const oficinas = useApi<Oficina[]>(tipo === 'manutencao' ? '/oficinas' : null);

  const [campos, setCampos] = useState<Record<string, string>>({});
  const [assinatura, setAssinatura] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const set = (k: string, v: string) => setCampos((c) => ({ ...c, [k]: v }));

  const total = campos.litros && campos.valorLitro
    ? (Number(campos.litros) * Number(campos.valorLitro))
    : 0;

  async function confirmar() {
    setSalvando(true);
    setErro(null);
    try {
      if (tipo === 'abastecimento') {
        if (!assinatura) throw new ApiError(400, 'É preciso assinar antes de confirmar.');
        await api.post(`/viagens/${viagemId}/abastecimentos`, {
          postoId: campos.postoId,
          litros: Number(campos.litros),
          valorLitro: Number(campos.valorLitro),
          assinaturaBase64: assinatura,
          observacao: campos.observacao || null,
        });
      } else {
        await api.post(`/viagens/${viagemId}/eventos/${tipo}`, {
          valor: campos.valor ? Number(campos.valor) : null,
          local: campos.local || null,
          motivo: campos.motivo || null,
          oficinaId: campos.oficinaId || null,
          servico: campos.servico || null,
          titulo: campos.titulo || null,
          descricao: campos.descricao || null,
          observacao: campos.observacao || null,
        });
      }
      onRegistrado();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível registrar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Registrar {tipo}</h3>

        {tipo === 'abastecimento' && (
          <>
            <label className="campo">
              <span>Posto</span>
              <select value={campos.postoId ?? ''} onChange={(e) => set('postoId', e.target.value)}>
                <option value="">Selecione o posto</option>
                {postos.dados?.map((p) => (
                  <option key={p.id} value={p.id}>{p.nome} — {p.cidade}/{p.estado}</option>
                ))}
              </select>
            </label>
            {postos.dados?.length === 0 && (
              <p className="modal__dica">
                Nenhum posto credenciado ainda. A gestão cadastra em Administração →
                Postos; a lista aparece em <Link to="/credenciados">Credenciados</Link>.
              </p>
            )}
            <div className="campo-linha">
              <label className="campo"><span>Litros</span>
                <input type="number" step="0.01" onChange={(e) => set('litros', e.target.value)} /></label>
              <label className="campo"><span>Valor por litro</span>
                <input type="number" step="0.001" onChange={(e) => set('valorLitro', e.target.value)} /></label>
            </div>
            <div className="calc">Total <strong>R$ {brl(total)}</strong></div>
            <label className="campo"><span>Assinatura do motorista</span></label>
            <SignaturePad onChange={setAssinatura} />
          </>
        )}

        {tipo === 'pedagio' && (
          <div className="campo-linha">
            <label className="campo"><span>Nome / local</span>
              <input placeholder="Praça de pedágio BR-163" onChange={(e) => set('local', e.target.value)} /></label>
            <label className="campo campo--curto"><span>Valor</span>
              <input type="number" step="0.01" onChange={(e) => set('valor', e.target.value)} /></label>
          </div>
        )}

        {tipo === 'multa' && (
          <>
            <label className="campo"><span>Motivo</span>
              <input placeholder="Excesso de velocidade" onChange={(e) => set('motivo', e.target.value)} /></label>
            <div className="campo-linha">
              <label className="campo"><span>Local (opcional)</span>
                <input placeholder="Próximo a Sorriso/MT" onChange={(e) => set('local', e.target.value)} /></label>
              <label className="campo campo--curto"><span>Valor</span>
                <input type="number" step="0.01" onChange={(e) => set('valor', e.target.value)} /></label>
            </div>
          </>
        )}

        {tipo === 'manutencao' && (
          <>
            <label className="campo">
              <span>Oficina</span>
              <select value={campos.oficinaId ?? ''} onChange={(e) => set('oficinaId', e.target.value)}>
                <option value="">Selecione a oficina</option>
                {oficinas.dados?.map((o) => (
                  <option key={o.id} value={o.id}>{o.nome} — {o.cidade}/{o.estado}</option>
                ))}
              </select>
            </label>
            {oficinas.dados?.length === 0 && (
              <p className="modal__dica">
                Nenhuma oficina credenciada ainda. A gestão cadastra em Administração →
                Oficinas; a lista aparece em <Link to="/credenciados">Credenciados</Link>.
              </p>
            )}
            <label className="campo"><span>Serviço realizado</span>
              <input placeholder="Reparo do motor após colisão" onChange={(e) => set('servico', e.target.value)} /></label>
            <label className="campo campo--curto"><span>Valor</span>
              <input type="number" step="0.01" onChange={(e) => set('valor', e.target.value)} /></label>
          </>
        )}

        {tipo === 'ocorrencia' && (
          <>
            <label className="campo"><span>Título</span>
              <input placeholder="Atraso por congestionamento" onChange={(e) => set('titulo', e.target.value)} /></label>
            <label className="campo"><span>Descrição</span>
              <textarea rows={3} onChange={(e) => set('descricao', e.target.value)} /></label>
          </>
        )}

        {erro && <div className="modal__erro">{erro}</div>}

        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={confirmar} disabled={salvando}>
            {salvando ? 'Salvando...' : 'Registrar'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ---------------- Modal de finalização ---------------- */
function ModalFinalizar({ numero, onFechar, onConfirmar }: {
  numero: number; onFechar: () => void; onConfirmar: (obs: string, avaria: boolean) => Promise<void>;
}) {
  const [obs, setObs] = useState('');
  const [avaria, setAvaria] = useState(false);
  const [salvando, setSalvando] = useState(false);

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Entrega concluída?</h3>
        <p className="modal__hint">Isso encerra a viagem #{numero} e libera você para criar a próxima.</p>
        <label className="campo">
          <span>Observação final (opcional)</span>
          <textarea rows={3} value={obs} onChange={(e) => setObs(e.target.value)} />
        </label>
        <label className="campo campo--inline">
          <input type="checkbox" checked={avaria} onChange={(e) => setAvaria(e.target.checked)} />
          <span>Houve avaria na carga</span>
        </label>
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" disabled={salvando}
            onClick={async () => { setSalvando(true); await onConfirmar(obs, avaria); }}>
            {salvando ? 'Finalizando...' : 'Confirmar entrega'}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Viagem pega na Logística, ainda parada. Mostra o que foi contratado e o
 * caminho para começar: gerar os documentos e iniciar.
 */
/**
 * Fim da viagem.
 *
 * A demanda não é do motorista: ela é um contrato da transportadora que várias
 * viagens vão abatendo. Por isso o caminho natural depois de entregar é emendar
 * a próxima da mesma demanda — e não voltar à Logística procurar de novo.
 */
function ViagemConcluida({ viagem }: { viagem: Viagem }) {
  const navigate = useNavigate();
  const retida = viagem.conferencia === 'RETIDA';

  return (
    <div className="viagem">
      <section className="viagem__aguardando">
        <span className="viagem__aguardando-selo">Viagem #{viagem.numero} · entregue</span>
        <h1>{viagem.origem} → {viagem.destino}</h1>
        <p>
          {viagem.carga} · {(viagem.pesoKg / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} t
          {viagem.demandaNumero != null && ` · demanda #${viagem.demandaNumero}`}
        </p>

        {retida ? (
          <div className="modal__erro">
            Retida na conferência: {viagem.motivosConferencia}
            <br />Ela só abate a demanda e entra no acerto depois que a gestão liberar.
          </div>
        ) : (
          <p className="viagem__aguardando-dica">
            Entrega confirmada — o peso já foi abatido da demanda e o frete entrou no caixa.
          </p>
        )}

        <div className="viagem__aguardando-acoes">
          {viagem.demandaId && (
            <button className="btn"
                    onClick={() => navigate('/logistica', { state: { abrirDemanda: viagem.demandaId } })}>
              Próxima viagem desta demanda
            </button>
          )}
          <Link className="btn btn--ghost" to={`/documentos?viagem=${viagem.id}`}
                style={{ textDecoration: 'none' }}>
            Ver documentos
          </Link>
          <Link className="btn btn--ghost" to="/historico" style={{ textDecoration: 'none' }}>
            Minhas viagens
          </Link>
        </div>
      </section>
    </div>
  );
}

function ViagemAIniciar({ viagem, aoIniciar }: { viagem: Viagem; aoIniciar: () => void }) {
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  // Documentos primeiro: a viagem só sai do pátio com NF, CT-e e MDF-e.
  const partida = async () => {
    await api.post(`/viagens/${viagem.id}/documentos`);
    await api.post(`/viagens/${viagem.id}/iniciar`);
  };

  return (
    <div className="viagem">
      <section className="viagem__aguardando">
        <span className="viagem__aguardando-selo">Viagem #{viagem.numero} · aguardando início</span>
        <h1>{viagem.origem} → {viagem.destino}</h1>
        <p>
          {viagem.carga} · {(viagem.pesoKg / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} t
          {viagem.demandaNumero != null && ` · demanda #${viagem.demandaNumero}`}
        </p>

        <dl className="viagem__aguardando-dados">
          <div><dt>Caminhão</dt><dd>{viagem.caminhao} · {viagem.placaCaminhao}</dd></div>
          {viagem.carreta && <div><dt>Carreta</dt><dd>{viagem.carreta}</dd></div>}
          {viagem.valorFrete != null && (
            <div><dt>Frete</dt>
              <dd>{viagem.valorFrete.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</dd>
            </div>
          )}
        </dl>

        <p className="viagem__aguardando-dica">
          Carregue no jogo e ligue o agente de telemetria antes de iniciar —
          viagem sem telemetria fica retida na conferência.
        </p>

        {erro && <div className="modal__erro">{erro}</div>}
        <div className="viagem__aguardando-acoes">
          <button className="btn" onClick={() => { setErro(null); setOcupado(true); }}>
            Gerar documentos e iniciar viagem
          </button>
          <Link className="btn btn--ghost" to="/telemetria" style={{ textDecoration: 'none' }}>
            Conferir telemetria
          </Link>
          <Link className="btn btn--ghost" to={`/documentos?viagem=${viagem.id}`}
                style={{ textDecoration: 'none' }}>
            Ver documentos
          </Link>
        </div>
      </section>

      {ocupado && (
        <Processo
          etapas={['Emitindo Nota Fiscal', 'Emitindo CT-e', 'Emitindo MDF-e', 'Liberando a saída do pátio']}
          sucesso="Documentos emitidos. Boa viagem!"
          trabalho={partida}
          aoConcluir={aoIniciar}
          aoFalhar={(m) => { setOcupado(false); setErro(m); }}
        />
      )}
    </div>
  );
}
