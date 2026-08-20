import { useApi } from '../hooks/useApi';
import { ViagemResumo } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import './Ranking.css';

const brl = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });

export default function Ranking() {
  const { dados: viagens, carregando, erro, recarregar } = useApi<ViagemResumo[]>('/viagens/empresa');

  if (carregando) return <Carregando />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  // Viagem retida pela conferência não pontua até um gestor liberar.
  const concluidas = (viagens ?? [])
    .filter((v) => v.status === 'CONCLUIDA' && v.conferencia !== 'RETIDA');
  const retidas = (viagens ?? []).filter((v) => v.conferencia === 'RETIDA').length;

  // Agrupa por motorista a partir das viagens reais
  const porMotorista = new Map<string, { viagens: number; frete: number; peso: number }>();
  concluidas.forEach((v) => {
    const atual = porMotorista.get(v.motorista) ?? { viagens: 0, frete: 0, peso: 0 };
    porMotorista.set(v.motorista, {
      viagens: atual.viagens + 1,
      frete: atual.frete + (v.valorFrete ?? 0),
      peso: atual.peso + (v.pesoKg ?? 0),
    });
  });

  const ranking = [...porMotorista.entries()]
    .map(([nome, d]) => ({ nome, ...d }))
    .sort((a, b) => b.viagens - a.viagens);

  if (ranking.length === 0) {
    return (
      <div className="rank">
        <header className="rank__header">
          <h1>Ranking</h1>
          <p>Quem mais rodou pela LK Transportes</p>
        </header>
        <Vazio titulo="Ainda não há viagens concluídas"
          descricao="O ranking é montado a partir das viagens finalizadas." />
      </div>
    );
  }

  return (
    <div className="rank">
      <header className="rank__header">
        <h1>Ranking</h1>
        <p>
          Montado a partir das {concluidas.length} viagem(ns) concluída(s)
          {retidas > 0 && ` · ${retidas} fora da conta, retida(s) na conferência`}
        </p>
      </header>

      <div className="rank__panel">
        <div className="rank__panel-head">
          <span>Motorista</span><span>Viagens</span><span>Peso transportado</span><span>Frete gerado</span>
        </div>
        {ranking.map((m, i) => (
          <div className="rank__row" key={m.nome}>
            <div className="rank__row-nome">
              <span className={'rank__row-pos' + (i === 0 ? ' is-primeiro' : '')}>{i + 1}</span>
              {m.nome}
            </div>
            <span>{m.viagens}</span>
            <span>{brl(m.peso)} kg</span>
            <span className="rank__pontualidade">R$ {brl(m.frete)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
