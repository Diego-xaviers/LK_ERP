import { useState } from 'react';
import { api, ApiError, BASE, sessao } from '../api/client';
import { useUsuario } from '../auth';
import Icon from '../components/ui/Icon';
import './Download.css';

export default function Download() {
  const usuario = useUsuario();
  return (
    <div className="dl">
      <header className="dl__head">
        <div>
          <h1>Download do agente</h1>
          <p>Conecte o ETS2/ATS ao painel LK Transportes</p>
        </div>
      </header>
      <Instalacao motoristaId={usuario.id} />
    </div>
  );
}

export function Instalacao({ motoristaId }: { motoristaId: string }) {
  const [gerando, setGerando] = useState(false);
  const [baixando, setBaixando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  async function baixar() {
    setBaixando(true);
    setAviso(null);
    try {
      const r = await fetch(`${BASE}/telemetria/agente/${motoristaId}`, {
        headers: { Authorization: `Bearer ${sessao.token()}` },
      });
      if (!r.ok) throw new Error();
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = 'LK-Telemetria.zip';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setAviso('Não foi possível baixar o agente.');
    } finally {
      setBaixando(false);
    }
  }

  async function novoToken() {
    if (!confirm('Gerar um token novo invalida o agente já baixado. Continuar?')) return;
    setGerando(true);
    try {
      const r = await api.post<{ mensagem: string }>(`/telemetria/pareamento/${motoristaId}`);
      setAviso(r.mensagem);
    } catch (e) {
      setAviso(e instanceof ApiError ? e.message : 'Não foi possível gerar o token.');
    } finally {
      setGerando(false);
    }
  }

  return (
    <section className="dl__card">
      <h2>Ligue o seu jogo ao painel</h2>
      <p className="dl__nota">
        O agente lê a telemetria do Euro Truck Simulator 2 e envia para cá em tempo real.
        Ele só lê — não altera o jogo nem o seu save.
      </p>

      <ol className="dl__passos">
        <li>
          <strong>Instale o plugin SCS SDK.</strong> Precisa existir{' '}
          <code>scs-telemetry.dll</code> em{' '}
          <code>Euro Truck Simulator 2\bin\win_x64\plugins\</code>. Baixe em{' '}
          <a href="https://github.com/RenCloud/scs-sdk-plugin/releases" target="_blank" rel="noreferrer">
            RenCloud/scs-sdk-plugin
          </a>.
        </li>
        <li>
          <strong>Baixe o agente LK</strong> — ele já vem configurado com o seu token.
          <button className="btn dl__baixar" onClick={baixar} disabled={baixando}>
            <Icon name="arrowRight" size={15} />
            {baixando ? 'Preparando...' : 'Baixar LK-Telemetria.zip'}
          </button>
        </li>
        <li><strong>Descompacte</strong> a pasta e dê dois cliques em <code>LK-Telemetria.bat</code>.</li>
        <li>
          <strong>Deixe a janela aberta</strong> enquanto joga.
          O status em <em>Ao Vivo</em> acende sozinho quando o agente conectar.
        </li>
      </ol>

      <div className="dl__rodape">
        <button className="btn btn--ghost" onClick={novoToken} disabled={gerando}>
          {gerando ? 'Gerando...' : 'Gerar token novo'}
        </button>
        {aviso && <span className="dl__aviso">{aviso}</span>}
      </div>
    </section>
  );
}
