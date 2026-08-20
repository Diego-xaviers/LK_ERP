import { useApi } from '../hooks/useApi';
import { ViagemResumo } from '../api/tipos';
import { Carregando, Erro } from '../components/ui/Estado';
import MuralAvisos from '../components/mural/MuralAvisos';
import Icon from '../components/ui/Icon';
import { useUsuario } from '../auth';
import { Link } from 'react-router-dom';
import './Dashboard.css';

const brl = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });

export default function Dashboard() {
  const usuario = useUsuario();
  const { dados: viagens, carregando, erro, recarregar } = useApi<ViagemResumo[]>('/viagens/empresa');

  if (carregando) return <Carregando />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  const todas = viagens ?? [];
  const concluidas = todas.filter((v) => v.status === 'CONCLUIDA');
  const ativa = todas.find((v) => v.status === 'EM_ANDAMENTO' && v.motorista === usuario.nome);

  const faturamento = concluidas.reduce((s, v) => s + (v.valorFrete ?? 0), 0);
  const despesas = todas.reduce((s, v) => s + (v.totalDespesas ?? 0), 0);
  const kmTotal = todas.length;   // placeholder até haver odômetro por viagem
  const ticket = concluidas.length ? faturamento / concluidas.length : 0;

  const KPIS = [
    { icon: 'wallet' as const, tone: 'green', label: 'Faturamento', sub: 'Somatório dos fretes concluídos', valor: `R$ ${brl(faturamento)}` },
    { icon: 'receipt' as const, tone: 'blue', label: 'Ticket médio', sub: 'Valor médio por viagem', valor: `R$ ${brl(ticket)}` },
    { icon: 'fuel' as const, tone: 'amber', label: 'Despesas', sub: 'Abastecimento, pedágio, multas', valor: `R$ ${brl(despesas)}` },
    { icon: 'route' as const, tone: 'slate', label: 'Viagens', sub: `${concluidas.length} concluídas`, valor: String(kmTotal) },
  ];

  return (
    <div className="dash">
      <header className="dash__head">
        <div>
          <h1>Olá, {usuario.nome}</h1>
          <p>Visão geral da operação da LK Transportes</p>
        </div>
      </header>

      <div style={{ marginBottom: 'var(--sp-4)' }}>
        <MuralAvisos />
      </div>

      {ativa && (
        <Link to="/viagem" className="dash__ativa">
          <span className="dash__ativa-dot" />
          <div>
            <strong>Viagem #{ativa.numero} em andamento</strong>
            <span>{ativa.origem} → {ativa.destino} · {ativa.carga}</span>
          </div>
          <Icon name="arrowRight" size={17} />
        </Link>
      )}

      <section className="dash__kpis">
        {KPIS.map((k) => (
          <article className="kpi" key={k.label}>
            <div className="kpi__head">
              <span className={'kpi__icon kpi__icon--' + k.tone}><Icon name={k.icon} size={17} /></span>
            </div>
            <div className="kpi__value">{k.valor}</div>
            <div className="kpi__label">{k.label}</div>
            <div className="kpi__sub">{k.sub}</div>
          </article>
        ))}
      </section>

      <section className="dash__bottom">
        <article className="card">
          <header className="card__head">
            <div>
              <h2>Viagens recentes</h2>
              <span className="card__sub">Últimas registradas na plataforma</span>
            </div>
          </header>
          {todas.length === 0 ? (
            <p style={{ padding: '0 20px 20px', color: 'var(--ink-400)', fontSize: 'var(--text-base)' }}>
              Nenhuma viagem registrada ainda.
            </p>
          ) : (
            <table className="table">
              <thead>
                <tr><th>Viagem</th><th>Motorista</th><th>Rota</th><th>Despesas</th><th>Status</th></tr>
              </thead>
              <tbody>
                {todas.slice(0, 6).map((v) => (
                  <tr key={v.id}>
                    <td>#{v.numero}</td>
                    <td>{v.motorista}</td>
                    <td>{v.origem} → {v.destino}</td>
                    <td>R$ {brl(v.totalDespesas ?? 0)}</td>
                    <td>
                      <span className={'dash__status dash__status--' + v.status.toLowerCase()}>
                        {v.status === 'CONCLUIDA' ? 'Concluída' : v.status === 'EM_ANDAMENTO' ? 'Em rota' : 'Criada'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </article>
      </section>
    </div>
  );
}
