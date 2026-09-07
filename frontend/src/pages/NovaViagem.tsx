import { Link } from 'react-router-dom';
import { useApi } from '../hooks/useApi';
import { Viagem } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import { useUsuario } from '../auth';
import Icon from '../components/ui/Icon';
import './NovaViagem.css';

const brl = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
const hora = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

export default function NovaViagem() {
  const usuario = useUsuario();
  const { dados: viagem, carregando, erro, recarregar } =
    useApi<Viagem | null>(`/viagens/ativa/${usuario.id}`);

  if (carregando) return <Carregando texto="Buscando sua viagem..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  if (!viagem) {
    return (
      <div className="nv">
        <Vazio
          titulo="Nenhuma viagem em andamento"
          descricao="Aceite uma demanda na Logística para iniciar uma viagem."
          acao={
            <Link className="btn" to="/logistica" style={{ marginTop: 12, textDecoration: 'none' }}>
              Ver demandas abertas
            </Link>
          }
        />
      </div>
    );
  }

  const statusLabel: Record<string, string> = {
    CRIADA: 'Aguardando início',
    EM_ANDAMENTO: 'Em andamento',
    CONCLUIDA: 'Concluída',
  };

  return (
    <div className="nv">
      <header className="nv__head">
        <h1>Viagem #{viagem.numero}</h1>
        <span className={'nv__status nv__status--' + viagem.status.toLowerCase()}>
          {statusLabel[viagem.status] ?? viagem.status}
        </span>
      </header>

      {/* Rota */}
      <section className="nv__bloco nv__rota-card">
        <div className="nv__rota-linha">
          <span className="nv__cidade">{viagem.origem}</span>
          <Icon name="arrowRight" size={18} />
          <span className="nv__cidade nv__cidade--dest">{viagem.destino}</span>
        </div>
        <div className="nv__meta">
          <span>{viagem.carga}</span>
          {viagem.pesoKg != null && (
            <span>· {(viagem.pesoKg / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} t</span>
          )}
          {viagem.caminhao && <span>· {viagem.caminhao}</span>}
          {viagem.demandaNumero != null && <span>· Demanda #{viagem.demandaNumero}</span>}
        </div>
      </section>

      {/* Financeiro resumo */}
      {viagem.valorFrete != null && (
        <section className="nv__bloco nv__financeiro">
          <div className="nv__fin-item">
            <span>Frete</span>
            <strong className="verde">R$ {brl(viagem.valorFrete)}</strong>
          </div>
          {(viagem.totalDespesas ?? 0) > 0 && (
            <div className="nv__fin-item">
              <span>Despesas</span>
              <strong className="vermelho">R$ {brl(viagem.totalDespesas ?? 0)}</strong>
            </div>
          )}
        </section>
      )}

      {/* Eventos */}
      {viagem.eventos.length > 0 && (
        <section className="nv__bloco">
          <h2>Eventos</h2>
          <ol className="nv__timeline">
            {viagem.eventos.map((e) => (
              <li key={e.id} className="nv__timeline-item">
                <span className={'nv__marker nv__marker--' + e.tipo.toLowerCase()} />
                <span className="nv__hora">{hora(e.ocorridoEm)}</span>
                <span className="nv__desc">{e.descricao}</span>
                {e.valor != null && <span className="nv__valor">R$ {brl(e.valor)}</span>}
              </li>
            ))}
          </ol>
        </section>
      )}

      {/* Ações */}
      <div className="nv__rodape">
        <Link className="btn btn--ghost" to="/viagem" style={{ textDecoration: 'none' }}>
          Cockpit completo
        </Link>
        {viagem.status === 'EM_ANDAMENTO' && (
          <Link className="btn" to="/viagem" style={{ textDecoration: 'none' }}>
            <Icon name="flag" size={15} /> Registrar evento / Finalizar
          </Link>
        )}
      </div>
    </div>
  );
}
