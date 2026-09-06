import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, BASE, sessao } from '../api/client';
import { useApi } from '../hooks/useApi';
import { Viagem, Posto, Oficina, TelemetriaAtual, TelemetriaViagem as TViagemTipo } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import { useUsuario } from '../auth';
import SignaturePad from '../components/SignaturePad';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import './ModoViagem.css';

type TipoEvento = 'abastecimento' | 'manutencao' | 'pedagio' | 'multa' | 'ocorrencia';

const ACOES: { tipo: TipoEvento; label: string; icon: 'fuel' | 'wrench' | 'cone' | 'siren' | 'alertCircle' }[] = [
  { tipo: 'abastecimento', label: 'Abastecer',  icon: 'fuel' },
  { tipo: 'manutencao',   label: 'Manutenção', icon: 'wrench' },
  { tipo: 'pedagio',      label: 'Pedágio',    icon: 'cone' },
  { tipo: 'multa',        label: 'Multa',      icon: 'siren' },
  { tipo: 'ocorrencia',   label: 'Ocorrência', icon: 'alertCircle' },
];

const MARCADOR: Record<string, string> = {
  ABASTECIMENTO: 'abastecimento', MANUTENCAO: 'manutencao',
  PEDAGIO: 'pedagio', MULTA: 'multa', OCORRENCIA: 'ocorrencia',
};

const brl  = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
const hora = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
const fmt  = (v: number | null | undefined, d: number) =>
  (v == null || isNaN(v)) ? '—' : v.toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });

// ---------------------------------------------------------------------------

export default function ModoViagem() {
  const usuario = useUsuario();
  const { dados: viagem, carregando, erro, recarregar } =
    useApi<Viagem | null>(`/viagens/ativa/${usuario.id}`);

  const [aberto,     setAberto]     = useState<TipoEvento | null>(null);
  const [finalizando, setFinalizando] = useState(false);
  const [concluida,  setConcluida]  = useState<Viagem | null>(null);
  const [entrega,    setEntrega]    = useState<{ observacaoFinal: string; houveAvaria: boolean } | null>(null);
  const [erroEntrega, setErroEntrega] = useState<string | null>(null);

  if (carregando) return <Carregando texto="Buscando sua viagem..." />;
  if (erro)       return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (concluida)  return <ViagemConcluida viagem={concluida} />;

  const overlayEntrega = entrega && (
    <Processo
      etapas={['Registrando a entrega', 'Conferindo com a telemetria', 'Fechando o romaneio']}
      sucesso="Entrega registrada."
      trabalho={() => api.post<Viagem>(`/viagens/${viagem!.id}/finalizar`, entrega)}
      aoConcluir={(v) => { setEntrega(null); setConcluida(v); }}
      aoFalhar={(m)   => { setEntrega(null); setErroEntrega(m); }}
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

  if (viagem.status === 'CRIADA') return <ViagemAIniciar viagem={viagem} aoIniciar={recarregar} />;

  async function finalizar(observacaoFinal: string, houveAvaria: boolean) {
    setFinalizando(false);
    setEntrega({ observacaoFinal, houveAvaria });
  }

  return (
    <div className="vp">
      {/* ── Hero: rota + barra de progresso ── */}
      <HeroViagem viagem={viagem} motoristaId={usuario.id} />

      {/* ── Corpo: cockpit ← → painel ── */}
      <div className="vp__body">
        <CockpitTele motoristaId={usuario.id} viagemId={viagem.id} />

        <section className="vp__painel">
          {/* Botões de ação */}
          <div className="vp__acoes">
            {ACOES.map((a) => (
              <button key={a.tipo} className="acao" onClick={() => setAberto(a.tipo)}>
                <span className="acao__icone"><Icon name={a.icon} size={22} strokeWidth={1.5} /></span>
                {a.label}
              </button>
            ))}
          </div>

          {/* Timeline de eventos */}
          <div className="vp__card">
            <div className="vp__card-head">
              <h2>Eventos</h2>
              <span className="vp__badge">Despesas: <strong>R$ {brl(viagem.totalDespesas ?? 0)}</strong></span>
            </div>
            {viagem.eventos.length === 0 ? (
              <p className="vp__vazio">Nenhum evento ainda. Use os botões acima.</p>
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
          </div>

          {/* Documentos */}
          {viagem.documentos.length > 0 && (
            <div className="vp__card vp__docs">
              <div className="vp__card-head"><h2>Documentos</h2></div>
              <div className="vp__docs-lista">
                {viagem.documentos.map((d) => (
                  <span key={d.id} className="vp__doc-chip">
                    <span className="vp__doc-ok">✓</span> {d.tipo} · #{d.numero}
                  </span>
                ))}
              </div>
            </div>
          )}

          {erroEntrega && <div className="modal__erro">{erroEntrega}</div>}

          <button className="vp__finalizar" onClick={() => { setErroEntrega(null); setFinalizando(true); }}>
            <Icon name="flag" size={16} /> Finalizar viagem
          </button>
        </section>
      </div>

      {aberto && (
        <ModalEvento tipo={aberto} viagemId={viagem.id}
          onFechar={() => setAberto(null)} onRegistrado={() => { setAberto(null); recarregar(); }} />
      )}
      {finalizando && (
        <ModalFinalizar onFechar={() => setFinalizando(false)} onConfirmar={finalizar} numero={viagem.numero} />
      )}
      {overlayEntrega}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Hero com barra de progresso
// ---------------------------------------------------------------------------

function HeroViagem({ viagem, motoristaId }: { viagem: Viagem; motoristaId: string }) {
  const { dados: tv }   = useApi<TViagemTipo>(`/telemetria/viagem/${viagem.id}`);
  const { dados: tele } = useApi<TelemetriaAtual>(`/telemetria/atual/${motoristaId}`);

  const rodado    = (tv?.odometroAtualKm ?? 0) - (tv?.odometroInicialKm ?? 0);
  const planejado = tele?.distanciaPlanejadaKm ?? 0;
  const pct       = planejado > 0 ? Math.min(100, Math.max(0, (rodado / planejado) * 100)) : 0;

  return (
    <section className="vp__hero">
      <div className="vp__hero-top">
        <span className="vp__numero">Viagem #{viagem.numero}</span>
        <span className="vp__status"><span className="vp__status-dot" />Em andamento</span>
      </div>

      <div className="vp__rota-linha">
        <span className="vp__cidade">{viagem.origem}</span>
        <div className="vp__track">
          <div className="vp__track-fill" style={{ width: `${pct}%` }} />
          <span className="vp__truck" style={{ left: `${Math.max(0, Math.min(96, pct))}%` }}>🚛</span>
        </div>
        <span className="vp__cidade vp__cidade--dest">{viagem.destino}</span>
      </div>

      <div className="vp__hero-meta">
        <span>{viagem.carga} · {fmt(viagem.pesoKg / 1000, 1)} t</span>
        {planejado > 0
          ? <span>{fmt(rodado, 0)} / {fmt(planejado, 0)} km · {fmt(pct, 0)}%</span>
          : <span>{viagem.caminhao} · {viagem.placaCaminhao}</span>
        }
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Cockpit de telemetria (coluna esquerda)
// ---------------------------------------------------------------------------

function CockpitTele({ motoristaId, viagemId }: { motoristaId: string; viagemId: string }) {
  const { dados: tele, recarregar } = useApi<TelemetriaAtual>(`/telemetria/atual/${motoristaId}`);

  useEffect(() => {
    const t = setInterval(recarregar, 3000);
    return () => clearInterval(t);
  }, [recarregar]);

  const online = tele?.online ?? false;

  const tanquePct = tele?.combustivelL && tele?.combustivelCapacidadeL
    ? (tele.combustivelL / tele.combustivelCapacidadeL) * 100 : null;

  const danos = [
    { label: 'Motor',  v: tele?.danoMotorPct },
    { label: 'Câmbio', v: tele?.danoCambioPct },
    { label: 'Cabine', v: tele?.danoCabinePct },
    { label: 'Chassi', v: tele?.danoChassiPct },
    { label: 'Rodas',  v: tele?.danoRodasPct },
    { label: 'Carga',  v: tele?.danoCargaPct },
  ];

  return (
    <aside className="vp__cockpit">
      <div className="vp__cockpit-head">
        <span className={'vp__dot' + (online ? ' vp__dot--on' : '')} />
        <span>{online ? 'Agente conectado' : 'Agente offline'}</span>
      </div>

      {/* Velocímetro */}
      <div className="vp__card vp__vel-card">
        <Velocimetro kmh={tele?.velocidadeKmh} />
        <div className="vp__vel-info">
          <span className="vp__vel-num">{fmt(tele?.velocidadeKmh, 0)}</span>
          <span className="vp__vel-unit">km/h</span>
        </div>
        {tele?.marcha != null && (
          <div className="vp__vel-gear">M{tele.marcha}</div>
        )}
      </div>

      {/* Combustível */}
      <div className="vp__card">
        <div className="vp__card-head">
          <span><Icon name="fuel" size={13} /> Combustível</span>
          <span className="vp__badge">{fmt(tele?.combustivelL, 0)} L</span>
        </div>
        <div className="vp__barra-wrap">
          <div className="vp__barra">
            <div
              className={'vp__barra-fill' + ((tanquePct ?? 100) < 15 ? ' vp__barra-fill--alerta' : '')}
              style={{ width: `${tanquePct ?? 0}%` }}
            />
          </div>
          <span className="vp__barra-pct">{fmt(tanquePct, 0)}%</span>
        </div>
      </div>

      {/* Danos */}
      <div className="vp__card">
        <div className="vp__card-head"><span><Icon name="alertCircle" size={13} /> Estado do caminhão</span></div>
        <div className="vp__danos">
          {danos.map(({ label, v }) => {
            const pct = v ?? 0;
            return (
              <div key={label} className="vp__dano">
                <span className="vp__dano-label">{label}</span>
                <div className="vp__barra vp__barra--fina">
                  <div className={'vp__barra-fill' + (pct > 20 ? ' vp__barra-fill--alerta' : '')}
                       style={{ width: `${Math.min(100, pct)}%` }} />
                </div>
                <span className="vp__dano-val">{fmt(pct, 1)}%</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Informações do jogo */}
      {online && tele?.cargaNome && (
        <div className="vp__card">
          <div className="vp__card-head"><span><Icon name="route" size={13} /> No jogo</span></div>
          <dl className="vp__dl">
            {tele.cargaNome && <><dt>Carga</dt><dd>{tele.cargaNome}</dd></>}
            {tele.modeloCaminhao && <><dt>Caminhão</dt><dd>{tele.modeloCaminhao}</dd></>}
          </dl>
        </div>
      )}

      {/* Card do agente (quando offline) */}
      {!online && <CartaoAgente motoristaId={motoristaId} />}
    </aside>
  );
}

// ---------------------------------------------------------------------------
// Velocímetro SVG (semicírculo com stroke-dasharray)
// ---------------------------------------------------------------------------

const ARC_R = 52;
const ARC_CX = 70, ARC_CY = 72;
const ARC_LEN = Math.PI * ARC_R; // ≈ 163.4

function Velocimetro({ kmh }: { kmh?: number }) {
  const MAX = 160;
  const pct = Math.min(100, Math.max(0, ((kmh ?? 0) / MAX) * 100));
  const fill = ARC_LEN * (pct / 100);
  const offset = ARC_LEN - fill;

  const sx = ARC_CX - ARC_R, sy = ARC_CY;
  const ex = ARC_CX + ARC_R, ey = ARC_CY;

  return (
    <svg viewBox="0 0 140 80" className="vp__vel-svg" aria-hidden>
      {/* Fundo */}
      <path
        d={`M ${sx} ${sy} A ${ARC_R} ${ARC_R} 0 0 1 ${ex} ${ey}`}
        fill="none" stroke="var(--line-strong)" strokeWidth={10} strokeLinecap="round"
      />
      {/* Preenchimento colorido */}
      <path
        d={`M ${sx} ${sy} A ${ARC_R} ${ARC_R} 0 0 1 ${ex} ${ey}`}
        fill="none"
        stroke={pct > 80 ? 'var(--danger)' : pct > 60 ? 'var(--warning)' : 'var(--accent)'}
        strokeWidth={10} strokeLinecap="round"
        strokeDasharray={`${ARC_LEN}`}
        strokeDashoffset={`${offset}`}
        style={{ transition: 'stroke-dashoffset .3s ease, stroke .3s ease' }}
      />
      {/* Marcadores de velocidade */}
      {[0, 40, 80, 120, 160].map((v) => {
        const a = Math.PI * (1 - v / MAX);
        const r1 = ARC_R - 6, r2 = ARC_R + 2;
        const x1 = ARC_CX + r1 * Math.cos(a), y1 = ARC_CY - r1 * Math.sin(a);
        const x2 = ARC_CX + r2 * Math.cos(a), y2 = ARC_CY - r2 * Math.sin(a);
        return <line key={v} x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--ink-400)" strokeWidth={1.5} />;
      })}
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Card de instalação do agente (quando offline)
// ---------------------------------------------------------------------------

function CartaoAgente({ motoristaId }: { motoristaId: string }) {
  const [baixando, setBaixando] = useState(false);

  async function baixar() {
    setBaixando(true);
    try {
      const r = await fetch(`${BASE}/telemetria/agente/${motoristaId}`, {
        headers: { Authorization: `Bearer ${sessao.token()}` },
      });
      if (!r.ok) throw new Error();
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement('a');
      a.href = url; a.download = 'LK-Telemetria.zip'; a.click();
      URL.revokeObjectURL(url);
    } finally {
      setBaixando(false);
    }
  }

  return (
    <div className="vp__card vp__agente-card">
      <div className="vp__agente-icone">📡</div>
      <p className="vp__agente-txt">
        Ligue o agente de telemetria para ver velocidade, combustível e danos em tempo real.
      </p>
      <button className="btn vp__agente-btn" onClick={baixar} disabled={baixando}>
        {baixando ? 'Preparando...' : 'Baixar agente LK'}
      </button>
      <span className="vp__agente-hint">Windows · ETS2/ATS</span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Sub-componentes reutilizados dos modais (inalterados)
// ---------------------------------------------------------------------------

function ModalEvento({ tipo, viagemId, onFechar, onRegistrado }: {
  tipo: TipoEvento; viagemId: string; onFechar: () => void; onRegistrado: () => void;
}) {
  const postos  = useApi<Posto[]>(tipo === 'abastecimento' ? '/postos' : null);
  const oficinas = useApi<Oficina[]>(tipo === 'manutencao' ? '/oficinas' : null);

  const [campos, setCampos]   = useState<Record<string, string>>({});
  const [assinatura, setAssinatura] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro]       = useState<string | null>(null);

  const set = (k: string, v: string) => setCampos((c) => ({ ...c, [k]: v }));

  const total = campos.litros && campos.valorLitro
    ? (Number(campos.litros) * Number(campos.valorLitro)) : 0;

  async function confirmar() {
    setSalvando(true); setErro(null);
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
    } finally { setSalvando(false); }
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
                Nenhum posto credenciado. A gestão cadastra em Administração → Postos.
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
              <p className="modal__dica">Nenhuma oficina credenciada.</p>
            )}
            <label className="campo"><span>Serviço realizado</span>
              <input placeholder="Reparo do motor" onChange={(e) => set('servico', e.target.value)} /></label>
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

function ModalFinalizar({ numero, onFechar, onConfirmar }: {
  numero: number; onFechar: () => void; onConfirmar: (obs: string, avaria: boolean) => Promise<void>;
}) {
  const [obs, setObs]       = useState('');
  const [avaria, setAvaria] = useState(false);
  const [salvando, setSalvando] = useState(false);

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Entrega concluída?</h3>
        <p className="modal__hint">Isso encerra a viagem #{numero} e libera você para criar a próxima.</p>
        <label className="campo"><span>Observação final (opcional)</span>
          <textarea rows={3} value={obs} onChange={(e) => setObs(e.target.value)} /></label>
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
          </div>
        ) : (
          <p className="viagem__aguardando-dica">Entrega confirmada — frete entrou no caixa.</p>
        )}
        <div className="viagem__aguardando-acoes">
          {viagem.demandaId && (
            <button className="btn"
                    onClick={() => navigate('/logistica', { state: { abrirDemanda: viagem.demandaId } })}>
              Próxima viagem desta demanda
            </button>
          )}
          <Link className="btn btn--ghost" to={`/documentos?viagem=${viagem.id}`} style={{ textDecoration: 'none' }}>
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
  const [erro, setErro]       = useState<string | null>(null);

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
          Carregue no jogo e ligue o agente de telemetria antes de iniciar.
        </p>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="viagem__aguardando-acoes">
          <button className="btn" onClick={() => { setErro(null); setOcupado(true); }}>
            Gerar documentos e iniciar viagem
          </button>
          <Link className="btn btn--ghost" to={`/documentos?viagem=${viagem.id}`} style={{ textDecoration: 'none' }}>
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
