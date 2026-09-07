import { useEffect, useState } from 'react';
import { useApi } from '../hooks/useApi';
import './AoVivo.css';

interface MotoristaFrota {
  motoristaId: string;
  motoristaNome: string;
  velocidadeKmh?: number;
  combustivelL?: number;
  combustivelCapacidadeL?: number;
  danoMotorPct?: number;
  danoCambioPct?: number;
  danoCabinePct?: number;
  danoChassiPct?: number;
  danoRodasPct?: number;
  danoCargaPct?: number;
  cargaNome?: string;
  cargaMassaKg?: number;
  cidadeOrigem?: string;
  cidadeDestino?: string;
  empresaOrigem?: string;
  empresaDestino?: string;
  distanciaPlanejadaKm?: number;
  placaCaminhao?: string;
  modeloCaminhao?: string;
  emServico?: boolean;
  pausado?: boolean;
  atualizadoEm: string;
}

function n(v?: number | null): number {
  return v != null && Number.isFinite(v) ? v : 0;
}

function pct(atual: number, total: number): number {
  if (!total) return 0;
  return Math.min(100, Math.round((atual / total) * 100));
}

function fmt(v?: number | null, casas = 0): string {
  if (v == null || !Number.isFinite(v)) return '—';
  return v.toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
}

// ---------------------------------------------------------------------------

function LinhaMotorista({ m }: { m: MotoristaFrota }) {
  const vel = n(m.velocidadeKmh);
  const combAtual = n(m.combustivelL);
  const combTotal = n(m.combustivelCapacidadeL);
  const combPct = pct(combAtual, combTotal);

  const danoCabine = n(m.danoCabinePct);
  const danoChassi = n(m.danoChassiPct);
  const danoCarga = n(m.danoCargaPct);
  const danoPior = Math.max(danoCabine, danoChassi, danoCarga, n(m.danoMotorPct));

  return (
    <tr className="vivo__linha">
      {/* MOTORISTA */}
      <td className="vivo__cel vivo__cel--motorista">
        <span className="vivo__dot" />
        <div className="vivo__motorista-info">
          <span className="vivo__nome">{m.motoristaNome}</span>
          <span className="vivo__tag-linha">
            <span className="vivo__icon-inline">🚛</span>
            {m.modeloCaminhao ?? 'ETS2'}
            {m.placaCaminhao ? ` · ${m.placaCaminhao}` : ''}
          </span>
          <span className="vivo__tag-linha">
            <span className="vivo__icon-inline">🏭</span>
            {m.empresaOrigem ?? (m.emServico ? 'Em serviço' : 'Sem emprego')}
          </span>
        </div>
      </td>

      {/* DANO */}
      <td className="vivo__cel">
        <div className="vivo__dano-lista">
          <div className="vivo__dano-item"><span>🚛</span><span>{danoCabine.toFixed(0)}%</span></div>
          <div className="vivo__dano-item"><span>🚌</span><span>{danoChassi.toFixed(0)}%</span></div>
          <div className="vivo__dano-item"><span>📦</span><span>{danoCarga.toFixed(0)}%</span></div>
        </div>
        {danoPior > 20 && <span className="vivo__badge vivo__badge--vermelho">{danoPior.toFixed(0)}%</span>}
      </td>

      {/* CARGA */}
      <td className="vivo__cel">
        {m.cargaNome ? (
          <div className="vivo__carga-info">
            <div className="vivo__carga-item"><span>🏭</span><span>{m.empresaDestino ?? m.empresaOrigem ?? '—'}</span></div>
            <div className="vivo__carga-item"><span>📦</span><span>{m.cargaNome}</span></div>
            {n(m.cargaMassaKg) > 0 && (
              <div className="vivo__carga-item"><span>⚖️</span><span>{(n(m.cargaMassaKg) / 1000).toFixed(1)} T</span></div>
            )}
          </div>
        ) : (
          <span className="vivo__vazio-cel">Sem emprego</span>
        )}
      </td>

      {/* COMBUSTÍVEL */}
      <td className="vivo__cel">
        {combTotal > 0 ? (
          <>
            <div className="vivo__fuel-cabec">
              <span className="vivo__fuel-icone">⛽</span>
              <span className="vivo__fuel-tipo">DIESEL</span>
              <span className="vivo__fuel-pct">{combPct}%</span>
            </div>
            <div className="vivo__barra-wrap">
              <div className="vivo__barra" style={{
                width: `${combPct}%`,
                background: combPct > 30 ? '#f5a623' : '#ef4444',
              }} />
            </div>
            <div className="vivo__fuel-detalhe">
              <span>Combustível</span><span>{fmt(combAtual)} L</span>
            </div>
          </>
        ) : <span className="vivo__vazio-cel">—</span>}
      </td>

      {/* PROGRESSO */}
      <td className="vivo__cel">
        {m.cidadeOrigem && m.cidadeDestino ? (
          <>
            <div className="vivo__prog-rota">
              <span>{m.cidadeOrigem.toUpperCase()}</span>
              <span className="vivo__prog-seta">→</span>
              <span>{m.cidadeDestino.toUpperCase()}</span>
            </div>
            {m.distanciaPlanejadaKm && (
              <span className="vivo__tag-linha" style={{ marginTop: 4 }}>
                📍 {fmt(m.distanciaPlanejadaKm)} km planejados
              </span>
            )}
          </>
        ) : (
          <span className="vivo__vazio-cel">—</span>
        )}
      </td>

      {/* FINANÇAS */}
      <td className="vivo__cel">
        <span className="vivo__vazio-cel">—</span>
      </td>

      {/* VELOCIDADE */}
      <td className="vivo__cel vivo__cel--vel">
        <div className={'vivo__velcirc' + (m.pausado ? ' is-pausado' : '')}>
          <span className="vivo__vel-num">{vel.toFixed(0)}</span>
          <span className="vivo__vel-lim">km/h</span>
        </div>
      </td>
    </tr>
  );
}

// ---------------------------------------------------------------------------

export default function AoVivo() {
  const { dados, carregando, recarregar } = useApi<MotoristaFrota[]>('/telemetria/frota');
  const [hora, setHora] = useState('');

  useEffect(() => {
    const t = setInterval(() => {
      recarregar();
      setHora(new Date().toLocaleTimeString('pt-BR'));
    }, 5_000);
    setHora(new Date().toLocaleTimeString('pt-BR'));
    return () => clearInterval(t);
  }, [recarregar]);

  const motoristas = dados ?? [];
  const online = motoristas.length > 0;

  return (
    <div className="vivo">
      <header className="vivo__head">
        <div className="vivo__head-left">
          <span className={'vivo__dot vivo__dot--head' + (online ? ' is-online' : '')} />
          <h1>AO VIVO</h1>
        </div>
        <div className="vivo__head-right">
          <span className="vivo__head-subtitulo">TELEMETRIA EM TEMPO REAL DE MOTORISTAS CONECTADOS</span>
          {carregando && <span className="vivo__reconectando">Atualizando...</span>}
          {!carregando && hora && <span className="vivo__atualizado">{hora}</span>}
        </div>
      </header>

      {online ? (
        <div className="vivo__tabela-wrap">
          <table className="vivo__tabela">
            <thead>
              <tr>
                <th>MOTORISTA</th>
                <th>DANO</th>
                <th>CARGA</th>
                <th>COMBUSTÍVEL</th>
                <th>PROGRESSO</th>
                <th>FINANÇAS</th>
                <th>VELOCIDADE</th>
              </tr>
            </thead>
            <tbody>
              {motoristas.map((m) => (
                <LinhaMotorista key={m.motoristaId} m={m} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="vivo__vazio">
          <p>Nenhum motorista online agora.</p>
          <p className="vivo__vazio-sub">Abra o LK-Telemetria.bat e entre no jogo.</p>
        </div>
      )}
    </div>
  );
}
