import { useApi } from '../hooks/useApi';
import { Caminhao, Carreta } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import './Frota.css';

export default function Frota() {
  const caminhoes = useApi<Caminhao[]>('/caminhoes');
  const carretas = useApi<Carreta[]>('/carretas');

  if (caminhoes.carregando || carretas.carregando) return <Carregando />;
  if (caminhoes.erro) return <Erro mensagem={caminhoes.erro} aoTentarNovamente={caminhoes.recarregar} />;

  const cams = caminhoes.dados ?? [];
  const cars = carretas.dados ?? [];

  return (
    <div className="frota">
      <header className="frota__header">
        <h1>Frota</h1>
        <p>{cams.length} caminhões e {cars.length} carretas cadastrados</p>
      </header>

      <section className="frota__bloco">
        <h2>Caminhões</h2>
        {cams.length === 0 ? <Vazio titulo="Nenhum caminhão cadastrado" /> : (
          <div className="frota__grid">
            {cams.map((c) => (
              <article className={'frota__card frota__card--' + c.status.toLowerCase()} key={c.id}>
                <div className="frota__card-top">
                  <span className="frota__placa">{c.placa}</span>
                  <span className={'frota__status frota__status--' + c.status.toLowerCase()}>
                    {c.status === 'ATIVO' ? 'Ativo' : 'Manutenção'}
                  </span>
                </div>
                <div className="frota__modelo">{c.marca} {c.modelo}</div>
                <div className="frota__dono">
                  {c.dono ? <>Caminhão de <b>{c.dono.nome}</b></> : "Da empresa"}
                </div>
                <div className="frota__linha"><span>{c.identificacaoInterna ?? '—'}</span></div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="frota__bloco">
        <h2>Carretas</h2>
        {cars.length === 0 ? <Vazio titulo="Nenhuma carreta cadastrada" /> : (
          <div className="frota__grid">
            {cars.map((c) => (
              <article className="frota__card" key={c.id}>
                <div className="frota__card-top"><span className="frota__placa">{c.placa}</span></div>
                <div className="frota__modelo">{c.tipo}</div>
                <div className="frota__linha"><span>{c.identificacaoInterna ?? '—'}</span></div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
