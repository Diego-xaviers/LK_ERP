import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useSessao } from '../auth';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import { Caminhao, Carreta, Demanda } from '../api/tipos';
import './Logistica.css';

/** Mesmos tipos do cadastro de carretas em Administração. */
const TIPOS_REBOQUE = ['Graneleira', 'Baú', 'Bitrem', 'Frigorífica', 'Prancha'];

export default function Logistica() {
  const { eGestor } = useSessao();
  // Gestor precisa ver também as concluídas e canceladas; motorista só o que dá pra pegar.
  const rota = eGestor ? '/demandas' : '/demandas/abertas';
  const { dados, carregando, erro, recarregar } = useApi<Demanda[]>(rota);
  const [modal, setModal] = useState<'nova' | Demanda | null>(null);

  // Quem acabou de entregar chega aqui com a demanda no state: abre o modal dela
  // direto, para emendar a próxima viagem sem procurar o cartão na lista.
  const { state } = useLocation() as { state?: { abrirDemanda?: string } };
  const abrirDemanda = state?.abrirDemanda;
  useEffect(() => {
    if (!abrirDemanda || !dados) return;
    const d = dados.find((x) => x.id === abrirDemanda);
    if (d?.aceitaNovaViagem) setModal(d);
  }, [abrirDemanda, dados]);

  async function cancelar(d: Demanda) {
    if (!confirm(`Cancelar a demanda #${d.numero}? As viagens já feitas continuam valendo.`)) return;
    try {
      await api.post(`/demandas/${d.id}/cancelar`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível cancelar.');
    }
  }

  return (
    <div className="log">
      <header className="log__head">
        <div>
          <h1>Logística</h1>
          <p>{eGestor
            ? 'Contratos fechados com clientes — cada viagem entregue abate o saldo'
            : 'Contratos da transportadora: entre num e rode quantas viagens quiser'}</p>
        </div>
        {eGestor && (
          <button className="btn" onClick={() => setModal('nova')}>
            <Icon name="plus" size={15} /> Nova demanda
          </button>
        )}
      </header>

      {carregando ? <Carregando /> :
       erro ? <Erro mensagem={erro} aoTentarNovamente={recarregar} /> :
       !dados?.length ? (
        <Vazio titulo={eGestor ? 'Nenhuma demanda cadastrada' : 'Nenhuma demanda aberta agora'}
               descricao={eGestor ? 'Crie uma demanda para os motoristas rodarem.'
                                  : 'Assim que a gestão publicar um contrato, ele aparece aqui.'} />
      ) : (
        <div className="log__lista">
          {dados.map((d) => (
            <Cartao key={d.id} d={d} eGestor={eGestor}
                    aoIniciar={() => setModal(d)} aoCancelar={() => cancelar(d)} />
          ))}
        </div>
      )}

      {modal === 'nova' && (
        <ModalNovaDemanda onFechar={() => setModal(null)}
                          onSalvo={() => { setModal(null); recarregar(); }} />
      )}
      {modal && modal !== 'nova' && (
        <ModalIniciarDemanda demanda={modal} onFechar={() => setModal(null)} />
      )}
    </div>
  );
}

function Cartao({ d, eGestor, aoIniciar, aoCancelar }: {
  d: Demanda; eGestor: boolean; aoIniciar: () => void; aoCancelar: () => void;
}) {
  const pct = d.percentualConcluido;
  const falta = d.quantidadeTotalKg - d.quantidadeEntregueKg;

  return (
    <article className={'log__cartao is-' + d.status.toLowerCase()}>
      <header>
        <div>
          <span className="log__numero">Demanda #{d.numero}</span>
          <h2>{d.carga}</h2>
        </div>
        <span className={'log__status is-' + d.status.toLowerCase()}>
          {d.status === 'ABERTA' ? 'Aberta' : d.status === 'CONCLUIDA' ? 'Concluída' : 'Cancelada'}
        </span>
      </header>

      <div className="log__rota">
        <strong>{d.origem}</strong>
        <Icon name="arrowRight" size={15} />
        <strong>{d.destino}</strong>
      </div>
      <p className="log__empresas">{d.empresaRemetente} → {d.empresaDestinataria}</p>

      <div className="log__progresso">
        <div className="log__barra"><i style={{ width: `${pct}%` }} /></div>
        <span className="log__progresso-linha">
          <b>{pct.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%</b> concluído
          <em>faltam {ton(falta)} t de {ton(d.quantidadeTotalKg)} t</em>
        </span>
      </div>

      <dl className="log__dados">
        <div><dt>Frete</dt><dd>{brl(d.fretePorTonelada)}/t</dd></div>
        {d.valorCargaPorTonelada != null && (
          <div><dt>Carga</dt><dd>{brl(d.valorCargaPorTonelada)}/t</dd></div>
        )}
        <div><dt>Livre</dt><dd>{ton(d.saldoDisponivelKg)} t</dd></div>
        {d.reservadoKg > 0 && <div><dt>Em rota</dt><dd>{ton(d.reservadoKg)} t</dd></div>}
      </dl>

      {(d.prazoEntrega || d.caminhoesPermitidos.length > 0 || d.tiposReboquePermitidos.length > 0) && (
        <div className="log__regras">
          {d.prazoEntrega && (
            <span className={d.atrasada ? "is-atrasada" : ""}>
              <Icon name="clock" size={13} /> Prazo {data(d.prazoEntrega)}{d.atrasada && " — atrasada"}
            </span>
          )}
          {d.caminhoesPermitidos.length > 0 && (
            <span><Icon name="truck" size={13} /> {d.caminhoesPermitidos.map((c) => c.placa).join(", ")}</span>
          )}
          {d.tiposReboquePermitidos.length > 0 && (
            <span><Icon name="layers" size={13} /> {d.tiposReboquePermitidos.join(", ")}</span>
          )}
        </div>
      )}

      {d.observacoes && <p className="log__obs">{d.observacoes}</p>}

      <footer>
        {d.aceitaNovaViagem && !eGestor && (
          <button className="btn" onClick={aoIniciar}>Iniciar demanda</button>
        )}
        {d.aceitaNovaViagem && eGestor && (
          <button className="btn btn--ghost" onClick={aoIniciar}>Iniciar demanda</button>
        )}
        {eGestor && d.status === 'ABERTA' && (
          <button className="btn btn--ghost" onClick={aoCancelar}>Cancelar</button>
        )}
      </footer>
    </article>
  );
}

/**
 * Entrar na demanda. Não é "pegar a demanda para si": ela continua da
 * transportadora e vários motoristas rodam a mesma. Aqui se escolhe só quanto
 * vai nesta viagem — e ao finalizar, o Modo Viagem oferece emendar a próxima.
 */
function ModalIniciarDemanda({ demanda, onFechar }: { demanda: Demanda; onFechar: () => void }) {
  const navigate = useNavigate();
  const { dados: caminhoes } = useApi<Caminhao[]>('/caminhoes');
  const { dados: carretas } = useApi<Carreta[]>('/carretas');

  const [pesoT, setPesoT] = useState(() => Math.min(25, demanda.saldoDisponivelKg / 1000).toFixed(1));
  const [caminhaoId, setCaminhaoId] = useState('');
  const [carretaId, setCarretaId] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const pesoKg = Math.round(parseFloat(pesoT.replace(',', '.') || '0') * 1000);
  const freteEstimado = (pesoKg / 1000) * demanda.fretePorTonelada;
  const excede = pesoKg > demanda.saldoDisponivelKg;

  // Só oferece o equipamento que a gestão liberou nesta demanda.
  const permitidos = new Set(demanda.caminhoesPermitidos.map((c) => c.id));
  const caminhoesOk = permitidos.size ? (caminhoes ?? []).filter((c) => permitidos.has(c.id)) : (caminhoes ?? []);
  const tipos = demanda.tiposReboquePermitidos;
  const carretasOk = tipos.length
    ? (carretas ?? []).filter((c) => tipos.some((t) => t.toLowerCase() === c.tipo.toLowerCase()))
    : (carretas ?? []);
  const exigeCarreta = tipos.length > 0;

  const entrarNaDemanda = () => api.post<{ id: string }>(`/demandas/${demanda.id}/aceitar`, {
    pesoKg, caminhaoId, carretaId: carretaId || undefined,
  });

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Iniciar demanda #{demanda.numero}</h3>
        <p className="log__resumo-modal">
          {demanda.carga} · {demanda.origem} → {demanda.destino}
        </p>
        <p className="log__resumo-modal">
          A demanda segue da transportadora. Ao finalizar esta viagem você emenda a
          próxima daqui mesmo, e cada entrega abate o saldo até o contrato fechar.
        </p>

        <label className="campo">
          <span>Quanto você leva nesta viagem (toneladas)</span>
          <input value={pesoT} onChange={(e) => setPesoT(e.target.value)} inputMode="decimal" />
        </label>
        {excede && (
          <div className="log__aviso">
            Sobra só {ton(demanda.saldoDisponivelKg)} t livres nesta demanda.
          </div>
        )}

        <label className="campo">
          <span>Caminhão</span>
          <select value={caminhaoId} onChange={(e) => setCaminhaoId(e.target.value)}>
            <option value="">Selecione</option>
            {caminhoesOk.map((c) => (
              <option key={c.id} value={c.id}>{c.marca} {c.modelo} — {c.placa}</option>
            ))}
          </select>
        </label>

        <label className="campo">
          <span>{exigeCarreta ? `Reboque (obrigatório: ${tipos.join(", ")})` : "Carreta (opcional)"}</span>
          <select value={carretaId} onChange={(e) => setCarretaId(e.target.value)}>
            <option value="">{exigeCarreta ? "Selecione" : "Sem carreta"}</option>
            {carretasOk.map((c) => (
              <option key={c.id} value={c.id}>{c.tipo} — {c.placa}</option>
            ))}
          </select>
        </label>

        <div className="log__frete">
          <span>Frete desta viagem</span>
          <strong>{brl(freteEstimado)}</strong>
          <small>{brl(demanda.fretePorTonelada)}/t definido pela gestão — você não digita esse valor</small>
        </div>

        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn"
                  onClick={() => { setErro(null); setSalvando(true); }}
                  disabled={salvando || !caminhaoId || pesoKg <= 0 || excede || (exigeCarreta && !carretaId)}>
            Iniciar demanda
          </button>
        </div>
      </div>

      {salvando && (
        <Processo
          etapas={['Reservando a carga na demanda', 'Conferindo o equipamento', 'Abrindo a viagem']}
          sucesso="Viagem aberta. Carregue no jogo!"
          trabalho={entrarNaDemanda}
          // A viagem já nasce pronta: leva direto pra ela em vez de pedir pra procurar.
          aoConcluir={(v) => navigate('/viagem', { state: { viagemId: v.id } })}
          aoFalhar={(m) => { setSalvando(false); setErro(m); }}
        />
      )}
    </div>
  );
}

function ModalNovaDemanda({ onFechar, onSalvo }: { onFechar: () => void; onSalvo: () => void }) {
  const [f, setF] = useState({
    origem: '', destino: '', empresaRemetente: '', empresaDestinataria: '',
    carga: '', quantidadeT: '', fretePorTonelada: '', valorCargaPorTonelada: '', observacoes: '', prazoEntrega: '',
  });
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const { dados: frota } = useApi<Caminhao[]>('/caminhoes');
  const [caminhoesSel, setCaminhoesSel] = useState<string[]>([]);
  const [reboquesSel, setReboquesSel] = useState<string[]>([]);

  function campo(k: keyof typeof f, v: string) { setF((x) => ({ ...x, [k]: v })); }
  function alterna(lista: string[], set: (v: string[]) => void, valor: string) {
    set(lista.includes(valor) ? lista.filter((x) => x !== valor) : [...lista, valor]);
  }
  const num = (s: string) => parseFloat(s.replace(',', '.') || '0');

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      await api.post('/demandas', {
        origem: f.origem, destino: f.destino,
        empresaRemetente: f.empresaRemetente, empresaDestinataria: f.empresaDestinataria,
        carga: f.carga,
        quantidadeTotalKg: Math.round(num(f.quantidadeT) * 1000),
        fretePorTonelada: num(f.fretePorTonelada),
        valorCargaPorTonelada: f.valorCargaPorTonelada ? num(f.valorCargaPorTonelada) : undefined,
        observacoes: f.observacoes || undefined,
        prazoEntrega: f.prazoEntrega || undefined,
        caminhoesPermitidos: caminhoesSel,
        tiposReboquePermitidos: reboquesSel,
      });
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal modal--largo" onClick={(e) => e.stopPropagation()}>
        <h3>Nova demanda</h3>
        <div className="log__form">
          <label className="campo"><span>Origem</span>
            <input value={f.origem} onChange={(e) => campo('origem', e.target.value)} /></label>
          <label className="campo"><span>Destino</span>
            <input value={f.destino} onChange={(e) => campo('destino', e.target.value)} /></label>
          <label className="campo"><span>Empresa remetente</span>
            <input value={f.empresaRemetente} onChange={(e) => campo('empresaRemetente', e.target.value)} /></label>
          <label className="campo"><span>Empresa destinatária</span>
            <input value={f.empresaDestinataria} onChange={(e) => campo('empresaDestinataria', e.target.value)} /></label>
          <label className="campo campo--largo"><span>Carga</span>
            <input value={f.carga} onChange={(e) => campo('carga', e.target.value)} /></label>
          <label className="campo"><span>Quantidade total (toneladas)</span>
            <input value={f.quantidadeT} inputMode="decimal"
                   onChange={(e) => campo('quantidadeT', e.target.value)} /></label>
          <label className="campo"><span>Frete por tonelada (R$)</span>
            <input value={f.fretePorTonelada} inputMode="decimal"
                   onChange={(e) => campo('fretePorTonelada', e.target.value)} /></label>
          <label className="campo"><span>Valor da carga por tonelada (opcional)</span>
            <input value={f.valorCargaPorTonelada} inputMode="decimal"
                   onChange={(e) => campo('valorCargaPorTonelada', e.target.value)} /></label>
          <label className="campo"><span>Prazo de entrega</span>
            <input type="date" value={f.prazoEntrega}
                   onChange={(e) => campo('prazoEntrega', e.target.value)} /></label>

          <fieldset className="log__escolhas campo--largo">
            <legend>Caminhões permitidos <small>vazio = qualquer um da frota</small></legend>
            <div className="log__chips">
              {frota?.map((c) => (
                <button type="button" key={c.id}
                        className={'log__chip' + (caminhoesSel.includes(c.id) ? ' is-on' : '')}
                        onClick={() => alterna(caminhoesSel, setCaminhoesSel, c.id)}>
                  {c.marca} {c.modelo} · {c.placa}
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className="log__escolhas campo--largo">
            <legend>Tipos de reboque permitidos <small>vazio = qualquer um</small></legend>
            <div className="log__chips">
              {TIPOS_REBOQUE.map((t) => (
                <button type="button" key={t}
                        className={'log__chip' + (reboquesSel.includes(t) ? ' is-on' : '')}
                        onClick={() => alterna(reboquesSel, setReboquesSel, t)}>
                  {t}
                </button>
              ))}
            </div>
          </fieldset>

          <label className="campo campo--largo"><span>Observações</span>
            <textarea rows={2} value={f.observacoes}
                      onChange={(e) => campo('observacoes', e.target.value)} /></label>
        </div>

        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={salvar} disabled={salvando}>
            {salvando ? 'Salvando...' : 'Publicar demanda'}
          </button>
        </div>
      </div>
    </div>
  );
}

function ton(kg: number) {
  return (kg / 1000).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}
function brl(v: number) {
  return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function data(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR');
}
