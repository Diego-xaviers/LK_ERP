import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useApi } from '../hooks/useApi';
import { Viagem } from '../api/tipos';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import DanfeDocument from '../components/documents/DanfeDocument';
import CteDocument from '../components/documents/CteDocument';
import MdfeDocument from '../components/documents/MdfeDocument';
import { useUsuario } from '../auth';
import './Documentos.css';

const ROTULO = { NF: 'DANFE', CTE: 'DACTE', MDFE: 'DAMDFE' } as const;

export default function Documentos() {
  const usuario = useUsuario();
  const [params] = useSearchParams();
  const viagemDaUrl = params.get('viagem');

  const { dados: viagens, carregando, erro, recarregar } =
    useApi<Viagem[]>(`/viagens/motorista/${usuario.id}`);

  const [viagemSelecionada, setViagemSelecionada] = useState<string | null>(viagemDaUrl);
  const [aba, setAba] = useState<'NF' | 'CTE' | 'MDFE'>('NF');

  if (carregando) return <Carregando texto="Buscando documentos..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  const comDocumentos = (viagens ?? []).filter((v) => v.documentos.length > 0);

  if (comDocumentos.length === 0) {
    return (
      <div className="docs">
        <header className="docs__head">
          <h1>Documentos</h1>
          <p>Gerados automaticamente a partir dos dados de cada viagem</p>
        </header>
        <Vazio
          titulo="Nenhum documento emitido ainda"
          descricao="Crie uma viagem e gere a documentação — a NF, o CT-e e o MDF-e aparecem aqui."
        />
      </div>
    );
  }

  const atual = comDocumentos.find((v) => v.id === viagemSelecionada) ?? comDocumentos[0];
  const documento = atual.documentos.find((d) => d.tipo === aba);

  return (
    <div className="docs">
      <header className="docs__head">
        <h1>Documentos</h1>
        <p>Gerados automaticamente a partir dos dados de cada viagem</p>
      </header>

      <div className="docs__layout">
        <aside className="docs__lista">
          <span className="docs__lista-titulo">Viagens</span>
          {comDocumentos.map((v) => (
            <button
              key={v.id}
              className={'docs__viagem' + (v.id === atual.id ? ' is-active' : '')}
              onClick={() => setViagemSelecionada(v.id)}
            >
              <strong>#{v.numero}</strong>
              <span>{v.origem} → {v.destino}</span>
              <em>{v.carga}</em>
            </button>
          ))}
        </aside>

        <section className="docs__visor">
          <div className="docs__abas">
            {(['NF', 'CTE', 'MDFE'] as const).map((t) => (
              <button
                key={t}
                className={aba === t ? 'is-active' : ''}
                onClick={() => setAba(t)}
                disabled={!atual.documentos.some((d) => d.tipo === t)}
              >
                {ROTULO[t]}
              </button>
            ))}
            <button className="docs__imprimir" onClick={() => window.print()}>
              Imprimir / PDF
            </button>
          </div>

          {documento ? (
            <div className="docs__papel">
              {aba === 'NF' && <DanfeDocument doc={documento} />}
              {aba === 'CTE' && <CteDocument doc={documento} />}
              {aba === 'MDFE' && <MdfeDocument doc={documento} />}
            </div>
          ) : (
            <Vazio titulo="Documento não gerado para esta viagem" />
          )}
        </section>
      </div>
    </div>
  );
}
