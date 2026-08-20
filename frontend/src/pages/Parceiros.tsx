import { useState } from 'react';
import { useApi } from '../hooks/useApi';
import { Posto, Oficina, EmpresaParceira } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import './Parceiros.css';

type Aba = 'postos' | 'oficinas' | 'empresas';

export default function Parceiros() {
  const [aba, setAba] = useState<Aba>('postos');
  const postos = useApi<Posto[]>('/postos');
  const oficinas = useApi<Oficina[]>('/oficinas');
  const empresas = useApi<EmpresaParceira[]>('/empresas');

  if (postos.carregando || oficinas.carregando || empresas.carregando) return <Carregando />;
  if (postos.erro) return <Erro mensagem={postos.erro} aoTentarNovamente={postos.recarregar} />;

  const lista = aba === 'postos' ? postos.dados : aba === 'oficinas' ? oficinas.dados : empresas.dados;

  return (
    <div className="parc">
      <header className="parc__head">
        <div>
          <h1>Credenciados</h1>
          <p>Postos, oficinas e empresas que fazem parte da rede da LK no mapa RBR</p>
        </div>
      </header>

      <div className="parc__abas">
        <button className={aba === 'postos' ? 'is-active' : ''} onClick={() => setAba('postos')}>
          Postos <span>{postos.dados?.length ?? 0}</span>
        </button>
        <button className={aba === 'oficinas' ? 'is-active' : ''} onClick={() => setAba('oficinas')}>
          Oficinas <span>{oficinas.dados?.length ?? 0}</span>
        </button>
        <button className={aba === 'empresas' ? 'is-active' : ''} onClick={() => setAba('empresas')}>
          Empresas parceiras <span>{empresas.dados?.length ?? 0}</span>
        </button>
      </div>

      {!lista?.length ? (
        <Vazio titulo="Nada cadastrado aqui ainda"
          descricao="Cadastre em Administração para aparecer nesta lista." />
      ) : (
        <div className="parc__grid">
          {aba === 'postos' && postos.dados?.map((p) => (
            <article className="parc__card" key={p.id}>
              <span className="parc__icone parc__icone--posto"><Icon name="fuel" size={18} /></span>
              <div className="parc__info"><strong>{p.nome}</strong><span>{p.cidade} — {p.estado}</span></div>
              <span className={'parc__tag ' + (p.ativo ? 'is-on' : 'is-off')}>{p.ativo ? 'Ativo' : 'Inativo'}</span>
            </article>
          ))}
          {aba === 'oficinas' && oficinas.dados?.map((o) => (
            <article className="parc__card" key={o.id}>
              <span className="parc__icone parc__icone--oficina"><Icon name="wrench" size={18} /></span>
              <div className="parc__info"><strong>{o.nome}</strong><span>{o.cidade} — {o.estado}</span></div>
              <span className={'parc__tag ' + (o.ativa ? 'is-on' : 'is-off')}>{o.ativa ? 'Ativo' : 'Inativo'}</span>
            </article>
          ))}
          {aba === 'empresas' && empresas.dados?.map((e) => (
            <article className="parc__card" key={e.id}>
              <span className="parc__icone parc__icone--empresa"><Icon name="building" size={18} /></span>
              <div className="parc__info">
                <strong>{e.nome}</strong>
                <span>{e.segmento ? `${e.segmento} · ` : ''}{e.cidade} — {e.estado}</span>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
