import { Link } from 'react-router-dom';
import { useApi } from '../hooks/useApi';
import { useSessao } from '../auth';
import { Erro } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import { Demanda, Usuario, Viagem } from '../api/tipos';
import './Gestao.css';

interface Painel { saldo: number; aPagar: number }

/**
 * Central do gestor: junta num lugar só o que hoje mora espalhado em Logística,
 * Conferência, Financeiro, Loja e Administração — com o número que importa de
 * cada área e o atalho para agir.
 */
export default function Gestao() {
  const { eGestor } = useSessao();

  const { dados: demandas } = useApi<Demanda[]>(eGestor ? '/demandas' : null);
  const { dados: retidas } = useApi<Viagem[]>(eGestor ? '/viagens/retidas' : null);
  const { dados: financeiro } = useApi<Painel>(eGestor ? '/financeiro/painel' : null);
  const { dados: motoristas } = useApi<Usuario[]>(eGestor ? '/usuarios' : null);

  if (!eGestor) {
    return <Erro mensagem="Esta área é da gestão." />;
  }

  const abertas = (demandas ?? []).filter((d) => d.status === 'ABERTA');
  const pendentes = (motoristas ?? []).filter((m) => m.statusAcesso === 'PENDENTE').length;

  return (
    <div className="gestao">
      <header className="gestao__head">
        <h1>Gestão</h1>
        <p>Tudo que a transportadora precisa administrar, num lugar só</p>
      </header>

      <section className="gestao__numeros">
        <Numero titulo="Em caixa" valor={brl(financeiro?.saldo ?? 0)} destaque />
        <Numero titulo="A pagar" valor={brl(financeiro?.aPagar ?? 0)}
                alerta={(financeiro?.aPagar ?? 0) > 0} />
        <Numero titulo="Demandas abertas" valor={String(abertas.length)} />
        <Numero titulo="Viagens retidas" valor={String(retidas?.length ?? 0)}
                alerta={(retidas?.length ?? 0) > 0} />
      </section>

      <section className="gestao__cards">
        <Card para="/logistica" icon="layers" titulo="Logística"
              descricao="Publicar demandas: carga, rota, quantidade, tarifa, prazo e que equipamento é permitido."
              rodape={`${abertas.length} aberta(s)`} />

        <Card para="/conferencia" icon="shield" titulo="Conferência"
              descricao="Viagens que o jogo não confirmou, e o mapa em que a transportadora opera."
              rodape={retidas?.length ? `${retidas.length} esperando decisão` : 'Nada retido'}
              alerta={!!retidas?.length} />

        <Card para="/financeiro" icon="wallet" titulo="Financeiro"
              descricao="Caixa da empresa, extrato e acerto de comissão com os motoristas."
              rodape={financeiro?.aPagar ? `${brl(financeiro.aPagar)} a acertar` : 'Sem acerto pendente'}
              alerta={!!financeiro?.aPagar} />

        <Card para="/loja" icon="wallet" titulo="Loja"
              descricao="Catálogo que você define: item, preço, categoria e estoque. Os motoristas gastam os créditos aqui."
              rodape="Cadastrar itens" />

        <Card para="/admin" icon="settings" titulo="Cadastros"
              descricao="Motoristas, caminhões (e de quem é cada um), carretas, postos, oficinas, empresas e avisos."
              rodape={pendentes ? `${pendentes} motorista(s) aguardando aprovação` : 'Frota e cadastros'}
              alerta={pendentes > 0} />

        <Card para="/habilitacao" icon="shield" titulo="Habilitação"
              descricao="Emitir, renovar e suspender CNH. Sem carteira válida o motorista não pega carga."
              rodape="Carteiras dos motoristas" />
      </section>
    </div>
  );
}

function Numero({ titulo, valor, destaque, alerta }: {
  titulo: string; valor: string; destaque?: boolean; alerta?: boolean;
}) {
  return (
    <div className={'gestao__numero' + (destaque ? ' is-destaque' : '')}>
      <span>{titulo}</span>
      <strong className={alerta ? 'is-alerta' : ''}>{valor}</strong>
    </div>
  );
}

function Card({ para, icon, titulo, descricao, rodape, alerta }: {
  para: string; icon: 'layers' | 'shield' | 'wallet' | 'settings';
  titulo: string; descricao: string; rodape: string; alerta?: boolean;
}) {
  return (
    <Link to={para} className="gestao__card">
      <header>
        <span className="gestao__icone"><Icon name={icon} size={18} /></span>
        <h2>{titulo}</h2>
      </header>
      <p>{descricao}</p>
      <footer className={alerta ? 'is-alerta' : ''}>
        {rodape}
        <Icon name="arrowRight" size={14} />
      </footer>
    </Link>
  );
}

const brl = (v: number) => (v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
