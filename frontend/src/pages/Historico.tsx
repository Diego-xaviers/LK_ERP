import { useState } from 'react';
import { useApi } from '../hooks/useApi';
import { Viagem } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import { useUsuario } from '../auth';
import Icon from '../components/ui/Icon';
import './Historico.css';

const brl = (v?: number) => (v ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 });
const data = (iso?: string) => (iso ? new Date(iso).toLocaleDateString('pt-BR') : '—');
const hora = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

const ROTULO_STATUS: Record<string, string> = {
  CRIADA: 'Criada', EM_ANDAMENTO: 'Em andamento', CONCLUIDA: 'Concluída',
};

const MARCADOR: Record<string, string> = {
  ABASTECIMENTO: 'abastecimento', MANUTENCAO: 'manutencao',
  PEDAGIO: 'pedagio', MULTA: 'multa', OCORRENCIA: 'ocorrencia',
};

export default function Historico() {
  const usuario = useUsuario();
  const { dados: viagens, carregando, erro, recarregar } =
    useApi<Viagem[]>(`/viagens/motorista/${usuario.id}`);
  const [aberta, setAberta] = useState<string | null>(null);

  if (carregando) return <Carregando texto="Carregando suas viagens..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!viagens?.length) {
    return <Vazio titulo="Você ainda não fez nenhuma viagem" descricao="Assim que registrar a primeira, ela aparece aqui." />;
  }

  return (
    <div className="hist">
      <header className="hist__head">
        <h1>Minhas viagens</h1>
        <p>{viagens.length} viagem(ns) registrada(s)</p>
      </header>

      <div className="hist__lista">
        {viagens.map((v) => {
          const expandida = aberta === v.id;
          return (
            <article className="hist__card" key={v.id}>
              <button className="hist__resumo" onClick={() => setAberta(expandida ? null : v.id)}>
                <span className="hist__numero">#{v.numero}</span>
                <span className="hist__rota">
                  {v.origem} <Icon name="arrowRight" size={14} /> {v.destino}
                </span>
                <span className="hist__carga">{v.carga}</span>
                <span className="hist__data">{data(v.criadaEm)}</span>
                <span className={'hist__status hist__status--' + v.status.toLowerCase()}>
                  {ROTULO_STATUS[v.status]}
                </span>
                <span className="hist__seta" data-aberta={expandida}>
                  <Icon name="arrowRight" size={15} />
                </span>
              </button>

              {expandida && (
                <div className="hist__detalhe">
                  <div className="hist__col">
                    <h3>Linha do tempo</h3>
                    {v.eventos.length === 0 ? (
                      <p className="hist__vazio">Nenhum evento registrado.</p>
                    ) : (
                      <ol className="hist__timeline">
                        {v.eventos.map((e) => (
                          <li key={e.id}>
                            <span className={'hist__marker hist__marker--' + MARCADOR[e.tipo]} />
                            <span className="hist__hora">{hora(e.ocorridoEm)}</span>
                            <span className="hist__desc">{e.descricao}</span>
                            {e.valor != null && <span className="hist__valor">R$ {brl(e.valor)}</span>}
                          </li>
                        ))}
                        {v.finalizadaEm && (
                          <li>
                            <span className="hist__marker hist__marker--fim" />
                            <span className="hist__hora">{hora(v.finalizadaEm)}</span>
                            <span className="hist__desc">Entrega concluída</span>
                          </li>
                        )}
                      </ol>
                    )}
                  </div>

                  <div className="hist__col hist__col--lado">
                    <h3>Resumo financeiro</h3>
                    <dl className="hist__resumo-fin">
                      {(['ABASTECIMENTO', 'PEDAGIO', 'MANUTENCAO', 'MULTA'] as const).map((tipo) => {
                        const soma = v.eventos
                          .filter((e) => e.tipo === tipo)
                          .reduce((s, e) => s + (e.valor ?? 0), 0);
                        return (
                          <div key={tipo}>
                            <dt>{tipo.charAt(0) + tipo.slice(1).toLowerCase()}</dt>
                            <dd>R$ {brl(soma)}</dd>
                          </div>
                        );
                      })}
                      <div className="hist__total">
                        <dt>Total de despesas</dt>
                        <dd>R$ {brl(v.totalDespesas)}</dd>
                      </div>
                    </dl>

                    <h3 style={{ marginTop: 16 }}>Documentos</h3>
                    {v.documentos.length === 0 ? (
                      <p className="hist__vazio">Nenhum documento gerado.</p>
                    ) : (
                      <div className="hist__docs">
                        {v.documentos.map((d) => (
                          <span className="hist__doc" key={d.id}>
                            {d.tipo === 'NF' ? 'NF' : d.tipo === 'CTE' ? 'CT-e' : 'MDF-e'}
                            <em>nº {String(d.numero).padStart(6, '0')}</em>
                          </span>
                        ))}
                      </div>
                    )}

                    {v.observacaoFinal && (
                      <>
                        <h3 style={{ marginTop: 16 }}>Observação final</h3>
                        <p className="hist__obs">{v.observacaoFinal}</p>
                      </>
                    )}
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </div>
  );
}
