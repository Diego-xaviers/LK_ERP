import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import { Viagem } from '../api/tipos';
import './Conferencia.css';

/**
 * Fila do gestor: viagens que a telemetria não confirmou. Elas já contam no
 * histórico do motorista — o que está segurado é a pontuação e o pagamento.
 */
export default function Conferencia() {
  const { dados, carregando, erro, recarregar } = useApi<Viagem[]>('/viagens/retidas');
  const [liberando, setLiberando] = useState<Viagem | null>(null);

  if (carregando) return <Carregando texto="Carregando a fila..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  return (
    <div className="conf">
      <header className="conf__head">
        <div>
          <h1>Conferência</h1>
          <p>Viagens que o jogo não confirmou — pontuação e pagamento retidos</p>
        </div>
        {!!dados?.length && <span className="conf__contador">{dados.length} na fila</span>}
      </header>

      {!dados?.length ? (
        <Vazio titulo="Nada retido"
               descricao="Todas as viagens concluídas bateram com a telemetria." />
      ) : (
        <div className="conf__lista">
          {dados.map((v) => (
            <article className="conf__cartao" key={v.id}>
              <header>
                <div>
                  <span className="conf__numero">Viagem #{v.numero}</span>
                  <h2>{v.motorista}</h2>
                </div>
                <span className="conf__valor">{brl(v.valorFrete ?? 0)}</span>
              </header>

              <p className="conf__rota">
                {v.origem} <Icon name="arrowRight" size={13} /> {v.destino}
                <em>{v.carga} · {(v.pesoKg / 1000).toFixed(1)} t</em>
              </p>

              <div className="conf__motivos">
                <strong><Icon name="alertCircle" size={14} /> Por que ficou retida</strong>
                <ul>
                  {(v.motivosConferencia ?? '').split('\n').filter(Boolean).map((m) => (
                    <li key={m}>{m}</li>
                  ))}
                </ul>
              </div>

              <footer>
                <button className="btn" onClick={() => setLiberando(v)}>
                  <Icon name="check" size={15} /> Liberar pontuação e pagamento
                </button>
              </footer>
            </article>
          ))}
        </div>
      )}

      <MapaDaEmpresa />

      {liberando && (
        <ModalLiberar viagem={liberando}
                      onFechar={() => setLiberando(null)}
                      onLiberado={() => { setLiberando(null); recarregar(); }} />
      )}
    </div>
  );
}

function ModalLiberar({ viagem, onFechar, onLiberado }: {
  viagem: Viagem; onFechar: () => void; onLiberado: () => void;
}) {
  const [observacao, setObservacao] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const liberar = () => api.post(`/viagens/${viagem.id}/liberar`, { observacao });

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Liberar viagem #{viagem.numero}</h3>
        <p className="conf__aviso-modal">
          A viagem volta a pontuar e fica liberada para pagamento. Fica registrado
          que foi você quem liberou.
        </p>
        <label className="campo">
          <span>Por que está liberando?</span>
          <textarea rows={3} value={observacao} autoFocus
                    placeholder="Ex.: conferido com o motorista, troca de carga combinada."
                    onChange={(e) => setObservacao(e.target.value)} />
        </label>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={() => { setErro(null); setSalvando(true); }} disabled={salvando}>
            Liberar
          </button>
        </div>
      </div>

      {salvando && (
        <Processo
          etapas={['Registrando a liberação', 'Creditando o frete no caixa', 'Abatendo a demanda']}
          sucesso="Viagem liberada."
          trabalho={liberar}
          aoConcluir={onLiberado}
          aoFalhar={(m) => { setSalvando(false); setErro(m); }}
        />
      )}
    </div>
  );
}


// ---------------------------------------------------------------- mapa

interface CidadeMapa { id: string; idJogo: string; nome?: string; vezesVista: number }
interface PainelMapa {
  modo: 'APRENDENDO' | 'ATIVO';
  temArea: boolean;
  minX?: number; maxX?: number; minZ?: number; maxZ?: number;
  margemMetros: number;
  cidades: CidadeMapa[];
}

/**
 * Prova de que a viagem rodou no mapa da transportadora.
 *
 * Não dá para embutir a lista de cidades do RBR no código — o mod muda. Então
 * o sistema aprende com as primeiras viagens reais e o gestor tranca depois de
 * conferir se a lista está limpa.
 */
export function MapaDaEmpresa() {
  const { dados, carregando, erro, recarregar } = useApi<PainelMapa>('/mapa');
  const [ocupado, setOcupado] = useState(false);

  if (carregando) return <Carregando texto="Carregando o mapa..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!dados) return null;

  async function chamar(caminho: string, metodo: 'post' | 'delete' = 'post') {
    setOcupado(true);
    try {
      if (metodo === 'delete') await api.delete(caminho);
      else await api.post(caminho);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível concluir.');
    } finally {
      setOcupado(false);
    }
  }

  const aprendendo = dados.modo === 'APRENDENDO';

  return (
    <section className="conf__mapa">
      <header>
        <div>
          <h2>Mapa da transportadora</h2>
          <p>
            {aprendendo
              ? 'Aprendendo com as viagens: nada é bloqueado ainda.'
              : 'Trancado: viagem em cidade fora desta lista é retida.'}
          </p>
        </div>
        <span className={'conf__modo is-' + dados.modo.toLowerCase()}>
          {aprendendo ? 'Aprendendo' : 'Trancado'}
        </span>
      </header>

      <div className="conf__cidades">
        {dados.cidades.length === 0 ? (
          <p className="conf__vazio-mapa">
            Nenhuma cidade aprendida. Rode uma viagem no RBR com o agente ligado.
          </p>
        ) : dados.cidades.map((c) => (
          <span className="conf__cidade" key={c.id}>
            <b>{c.nome || c.idJogo}</b>
            <em>{c.idJogo}</em>
            {aprendendo && (
              <button title="Aprendida por engano — esquecer"
                      onClick={() => chamar(`/mapa/cidades/${c.id}`, 'delete')}>×</button>
            )}
          </span>
        ))}
      </div>

      {dados.temArea && (
        <p className="conf__area">
          Área conhecida: X de {Math.round(dados.minX!).toLocaleString('pt-BR')} a{' '}
          {Math.round(dados.maxX!).toLocaleString('pt-BR')} · Z de{' '}
          {Math.round(dados.minZ!).toLocaleString('pt-BR')} a{' '}
          {Math.round(dados.maxZ!).toLocaleString('pt-BR')}
          <em> (folga de {(dados.margemMetros / 1000).toLocaleString('pt-BR')} km)</em>
        </p>
      )}

      <footer>
        {aprendendo ? (
          <button className="btn" disabled={ocupado || dados.cidades.length === 0}
                  onClick={() => chamar('/mapa/trancar')}>
            <Icon name="shield" size={15} /> Trancar neste mapa
          </button>
        ) : (
          <button className="btn btn--ghost" disabled={ocupado}
                  onClick={() => chamar('/mapa/aprender')}>
            Voltar a aprender
          </button>
        )}
        <span className="conf__dica-mapa">
          {aprendendo
            ? 'Tranque só depois de conferir que a lista tem apenas cidades do RBR.'
            : 'Cidades novas do mod não entram sozinhas — volte a aprender para incluí-las.'}
        </span>
      </footer>
    </section>
  );
}

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
