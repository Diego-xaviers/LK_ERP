import { useEffect, useState } from 'react';
import { api, ApiError, BASE, sessao } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useUsuario } from '../auth';
import { Carregando, Erro } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import { TelemetriaAtual, TelemetriaViagem, Viagem } from '../api/tipos';
import './Telemetria.css';

export default function Telemetria() {
  const usuario = useUsuario();
  const { dados, carregando, erro, recarregar } = useApi<TelemetriaAtual>(`/telemetria/atual/${usuario.id}`);
  const { dados: viagem } = useApi<Viagem>(`/viagens/ativa/${usuario.id}`);

  // O painel só faz sentido ao vivo — reconsulta enquanto a aba estiver aberta.
  useEffect(() => {
    const t = setInterval(recarregar, 3000);
    return () => clearInterval(t);
  }, [recarregar]);

  if (carregando && !dados) return <Carregando texto="Procurando o agente..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  const online = dados?.online ?? false;

  return (
    <div className="tele">
      <header className="tele__head">
        <div>
          <h1>Telemetria</h1>
          <p>O jogo alimentando o painel enquanto você dirige</p>
        </div>
        <span className={'tele__status' + (online ? ' is-online' : '')}>
          <span className="tele__dot" />
          {online ? 'Agente conectado' : 'Agente desconectado'}
        </span>
      </header>

      {online && dados ? <AoVivo t={dados} /> : <Instalacao motoristaId={usuario.id} />}

      {viagem && <ConferenciaViagem viagemId={viagem.id} numero={viagem.numero} />}
    </div>
  );
}

// ---------------------------------------------------------------------------

function AoVivo({ t }: { t: TelemetriaAtual }) {
  const tanquePct = t.combustivelL && t.combustivelCapacidadeL
    ? (t.combustivelL / t.combustivelCapacidadeL) * 100 : null;
  const danoPior = Math.max(
    t.danoMotorPct ?? 0, t.danoCambioPct ?? 0, t.danoCabinePct ?? 0,
    t.danoChassiPct ?? 0, t.danoRodasPct ?? 0,
  );

  return (
    <>
      <section className="tele__grid">
        <Medidor titulo="Velocidade" valor={fmt(t.velocidadeKmh, 0)} unidade="km/h" icon="gauge" />
        <Medidor titulo="Combustível" valor={fmt(t.combustivelL, 0)} unidade="L" icon="fuel"
                 barra={tanquePct} alerta={tanquePct !== null && tanquePct < 15} />
        <Medidor titulo="Dano" valor={fmt(danoPior, 1)} unidade="%" icon="alertCircle"
                 barra={danoPior} alerta={danoPior > 20} />
        <Medidor titulo="Odômetro" valor={fmt(t.odometroKm, 0)} unidade="km" icon="route" />
      </section>

      <section className="tele__cards">
        <div className="tele__card">
          <h2>No jogo agora</h2>
          <dl className="tele__lista">
            <Linha rotulo="Caminhão" valor={[t.modeloCaminhao, t.placaCaminhao].filter(Boolean).join(' · ')} />
            <Linha rotulo="Carga" valor={t.cargaNome} />
            <Linha rotulo="Peso" valor={t.cargaMassaKg ? `${fmt(t.cargaMassaKg / 1000, 1)} t` : undefined} />
            <Linha rotulo="Rota" valor={t.cidadeOrigem && t.cidadeDestino
              ? `${t.cidadeOrigem} → ${t.cidadeDestino}` : undefined} />
            <Linha rotulo="Remetente" valor={t.empresaOrigem} />
            <Linha rotulo="Destinatário" valor={t.empresaDestino} />
            <Linha rotulo="Distância planejada" valor={t.distanciaPlanejadaKm ? `${t.distanciaPlanejadaKm} km` : undefined} />
          </dl>
        </div>

        <div className="tele__card">
          <h2>Estado do caminhão</h2>
          <Desgaste rotulo="Motor" pct={t.danoMotorPct} />
          <Desgaste rotulo="Câmbio" pct={t.danoCambioPct} />
          <Desgaste rotulo="Cabine" pct={t.danoCabinePct} />
          <Desgaste rotulo="Chassi" pct={t.danoChassiPct} />
          <Desgaste rotulo="Rodas" pct={t.danoRodasPct} />
          <Desgaste rotulo="Carga" pct={t.danoCargaPct} />
        </div>
      </section>
    </>
  );
}

function Medidor({ titulo, valor, unidade, icon, barra, alerta }: {
  titulo: string; valor: string; unidade: string;
  icon: 'gauge' | 'fuel' | 'alertCircle' | 'route'; barra?: number | null; alerta?: boolean;
}) {
  return (
    <div className={'tele__medidor' + (alerta ? ' is-alerta' : '')}>
      <span className="tele__medidor-titulo"><Icon name={icon} size={14} /> {titulo}</span>
      <strong>{valor}<small>{unidade}</small></strong>
      {barra !== null && barra !== undefined && (
        <div className="tele__barra"><i style={{ width: `${Math.min(100, Math.max(0, barra))}%` }} /></div>
      )}
    </div>
  );
}

function Desgaste({ rotulo, pct }: { rotulo: string; pct?: number }) {
  const v = pct ?? 0;
  return (
    <div className="tele__desgaste">
      <span>{rotulo}</span>
      <div className="tele__barra tele__barra--fina">
        <i className={v > 20 ? 'is-alto' : ''} style={{ width: `${Math.min(100, v)}%` }} />
      </div>
      <b>{fmt(v, 1)}%</b>
    </div>
  );
}

function Linha({ rotulo, valor }: { rotulo: string; valor?: string }) {
  return (
    <>
      <dt>{rotulo}</dt>
      <dd>{valor && valor.length ? valor : '—'}</dd>
    </>
  );
}

// ---------------------------------------------------------------------------

function ConferenciaViagem({ viagemId, numero }: { viagemId: string; numero: number }) {
  const { dados } = useApi<TelemetriaViagem>(`/telemetria/viagem/${viagemId}`);
  if (!dados) return null;

  const distancia = dados.odometroInicialKm != null && dados.odometroAtualKm != null
    ? dados.odometroAtualKm - dados.odometroInicialKm : null;
  const gasto = dados.combustivelInicialL != null && dados.combustivelAtualL != null
    ? dados.combustivelInicialL - dados.combustivelAtualL + (dados.litrosAbastecidos ?? 0) : null;
  const dano = dados.danoInicialPct != null && dados.danoAtualPct != null
    ? dados.danoAtualPct - dados.danoInicialPct : null;

  const sinais = [
    dados.usouPilotoAutomatico && 'piloto automático usado',
    dados.usouEstacionamentoAutomatico && 'estacionamento automático usado',
    dados.saltos > 0 && `${dados.saltos} salto(s) de posição`,
    dados.divergencias,
  ].filter(Boolean) as string[];

  return (
    <section className="tele__card tele__conferencia">
      <h2>Conferência da viagem #{numero}</h2>
      <p className="tele__nota">
        O que o jogo reportou — separado do que foi digitado, para o gestor comparar.
      </p>

      <section className="tele__grid tele__grid--compacto">
        <Medidor titulo="Rodado" valor={fmt(distancia, 1)} unidade="km" icon="route" />
        <Medidor titulo="Combustível gasto" valor={fmt(gasto, 1)} unidade="L" icon="fuel" />
        <Medidor titulo="Abastecido" valor={fmt(dados.litrosAbastecidos, 1)} unidade="L" icon="fuel" />
        <Medidor titulo="Dano na viagem" valor={fmt(dano, 1)} unidade="%" icon="alertCircle"
                 alerta={(dano ?? 0) > 5} />
      </section>

      {sinais.length > 0 && (
        <div className="tele__sinais">
          <strong><Icon name="shield" size={15} /> Pontos de atenção</strong>
          <ul>{sinais.map((s) => <li key={s}>{s}</li>)}</ul>
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------

function Instalacao({ motoristaId }: { motoristaId: string }) {
  const [gerando, setGerando] = useState(false);
  const [baixando, setBaixando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  /**
   * O download vai por fetch, não por <a href>: o endpoint exige o token e um
   * link comum não manda cabeçalho de autorização.
   */
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
    <section className="tele__card tele__instalacao">
      <h2>Ligue o seu jogo ao painel</h2>
      <p className="tele__nota">
        O agente lê a telemetria do Euro Truck Simulator 2 e envia para cá. Ele só lê —
        não altera o jogo nem o seu save.
      </p>

      <ol className="tele__passos">
        <li>
          <strong>Confira o plugin.</strong> Precisa existir o arquivo{' '}
          <code>scs-telemetry.dll</code> em{' '}
          <code>Euro Truck Simulator 2\bin\win_x64\plugins</code>. Se não tiver, baixe em{' '}
          <a href="https://github.com/RenCloud/scs-sdk-plugin/releases" target="_blank" rel="noreferrer">
            RenCloud/scs-sdk-plugin
          </a>.
        </li>
        <li>
          <strong>Baixe o agente</strong> — ele já vem com o seu token dentro.
          <button className="btn tele__baixar" onClick={baixar} disabled={baixando}>
            <Icon name="arrowRight" size={15} />
            {baixando ? 'Preparando...' : 'Baixar LK-Telemetria.zip'}
          </button>
        </li>
        <li><strong>Descompacte</strong> a pasta e dê dois cliques em <code>LK-Telemetria.bat</code>.</li>
        <li><strong>Deixe a janela aberta</strong> enquanto joga. Esta tela acende sozinha quando ele conectar.</li>
      </ol>

      <div className="tele__rodape">
        <button className="btn btn--ghost" onClick={novoToken} disabled={gerando}>
          {gerando ? 'Gerando...' : 'Gerar token novo'}
        </button>
        {aviso && <span className="tele__aviso">{aviso}</span>}
      </div>
    </section>
  );
}

function fmt(v: number | null | undefined, casas: number) {
  if (v === null || v === undefined || Number.isNaN(v)) return '—';
  return v.toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
}
