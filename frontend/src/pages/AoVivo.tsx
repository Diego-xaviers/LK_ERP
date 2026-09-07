import { useEffect, useState } from 'react';
import { api } from '../api/client';
import './AoVivo.css';

interface MotoristaVivo {
  steamID?: string;
  steam_id?: string;
  username?: string;
  speed?: number;
  speedLimit?: number;
  fuelCurrent?: number;
  fuelCapacity?: number;
  fuelRange?: number;
  fuelType?: string;
  routeDistance?: number;
  distance?: number;
  income?: number;
  expense?: number;
  fines?: number;
  cargoMass?: number;
  cargoName?: string;
  cargoCompany?: string;
  damageCabin?: number;
  damageTrailer?: number;
  damageCargo?: number;
  game?: string;
  citySource?: string;
  cityDest?: string;
  eta?: number;
  [key: string]: unknown;
}

interface SnapshotResponse {
  online: boolean;
  atualizado: string | null;
  snapshot?: string;
}

function num(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function pct(atual: number, total: number): number {
  if (!total) return 0;
  return Math.min(100, Math.round((atual / total) * 100));
}

function nomeMotorista(m: MotoristaVivo): string {
  return m.username ?? m.steamID ?? m.steam_id ?? '—';
}

function parsearMotoristas(snapshotJson: string): MotoristaVivo[] {
  try {
    const raw = JSON.parse(snapshotJson);
    if (Array.isArray(raw)) return raw;
    if (Array.isArray(raw?.drivers)) return raw.drivers;
    if (Array.isArray(raw?.data)) return raw.data;
    if (Array.isArray(raw?.users)) return raw.users;
    if (raw?.steamID || raw?.username) return [raw];
    return [];
  } catch {
    return [];
  }
}

function formatarEta(segundos: number): string {
  if (!segundos) return '';
  const h = Math.floor(segundos / 3600);
  const m = Math.floor((segundos % 3600) / 60);
  return h > 0 ? `${h}H · ${String(m).padStart(2, '0')}:00` : `${m}M`;
}

// ---------------------------------------------------------------------------

function LinhaMotorista({ m }: { m: MotoristaVivo }) {
  const vel = num(m.speed);
  const limVel = num(m.speedLimit);
  const acima = vel > limVel && limVel > 0;

  const combustivelAtual = num(m.fuelCurrent);
  const combustivelTotal = num(m.fuelCapacity);
  const combustivelPct = pct(combustivelAtual, combustivelTotal);
  const combustivelRange = num(m.fuelRange);
  const fuelType = (m.fuelType ?? 'DIESEL').toUpperCase();

  const percorrido = num(m.distance);
  const restante = num(m.routeDistance);
  const totalKm = percorrido + restante;
  const progressoPct = pct(percorrido, totalKm);

  const danoCabine = num(m.damageCabin);
  const danoReboque = num(m.damageTrailer);
  const danoCarga = num(m.damageCargo);

  const income = num(m.income);
  const expense = num(m.expense);
  const fines = num(m.fines);

  return (
    <tr className="vivo__linha">
      {/* MOTORISTA */}
      <td className="vivo__cel vivo__cel--motorista">
        <span className="vivo__dot" />
        <div className="vivo__motorista-info">
          <span className="vivo__nome">{nomeMotorista(m)}</span>
          <span className="vivo__tag-linha">
            <span className="vivo__icon-inline">🚛</span>
            {m.cargoCompany ?? 'Sem emprego'}
          </span>
          <span className="vivo__tag-linha">
            <span className="vivo__icon-inline">🎮</span>
            {m.game ?? 'ETS2'}
          </span>
        </div>
      </td>

      {/* DANO */}
      <td className="vivo__cel">
        <div className="vivo__dano-lista">
          <div className="vivo__dano-item"><span>🚛</span><span>{danoCabine.toFixed(0)}%</span></div>
          <div className="vivo__dano-item"><span>🚌</span><span>{danoReboque.toFixed(0)}%</span></div>
          <div className="vivo__dano-item"><span>📦</span><span>{danoCarga.toFixed(0)}%</span></div>
        </div>
      </td>

      {/* CARGA */}
      <td className="vivo__cel">
        {m.cargoName ? (
          <div className="vivo__carga-info">
            <div className="vivo__carga-item"><span>🏭</span><span>{m.cargoCompany ?? '—'}</span></div>
            <div className="vivo__carga-item"><span>📦</span><span>{m.cargoName}</span></div>
            {num(m.cargoMass) > 0 && (
              <div className="vivo__carga-item"><span>⚖️</span><span>{num(m.cargoMass).toFixed(0)} T</span></div>
            )}
          </div>
        ) : (
          <span className="vivo__vazio-cel">—</span>
        )}
      </td>

      {/* COMBUSTÍVEL */}
      <td className="vivo__cel">
        <div className="vivo__fuel-cabec">
          <span className="vivo__fuel-icone">⛽</span>
          <span className="vivo__fuel-tipo">{fuelType}</span>
          <span className="vivo__fuel-pct">{combustivelPct}%</span>
        </div>
        <div className="vivo__barra-wrap">
          <div
            className="vivo__barra"
            style={{
              width: `${combustivelPct}%`,
              background: combustivelPct > 30 ? '#f5a623' : '#ef4444',
            }}
          />
        </div>
        <div className="vivo__fuel-detalhe">
          <span>Combustível</span><span>{combustivelAtual.toFixed(0)} L</span>
        </div>
        <div className="vivo__fuel-detalhe">
          <span>Intervalo</span><span>{combustivelRange.toFixed(0)} KM</span>
        </div>
      </td>

      {/* PROGRESSO */}
      <td className="vivo__cel">
        {totalKm > 0 ? (
          <>
            <div className="vivo__prog-cabec">
              <span className="vivo__prog-icone">👣</span>
              <span className="vivo__prog-label">PROGRESSO</span>
              <span className="vivo__prog-pct">{progressoPct}%</span>
            </div>
            <div className="vivo__barra-wrap">
              <div className="vivo__barra" style={{ width: `${progressoPct}%`, background: '#f5a623' }} />
            </div>
            <div className="vivo__prog-km">
              <span>{percorrido.toFixed(0)} / {totalKm.toFixed(0)} KM</span>
              {m.eta ? <span>{formatarEta(num(m.eta))}</span> : null}
            </div>
            {m.citySource && m.cityDest && (
              <div className="vivo__prog-rota">
                <span>{m.citySource.toUpperCase()}</span>
                <span className="vivo__prog-seta">→</span>
                <span>{m.cityDest.toUpperCase()}</span>
              </div>
            )}
          </>
        ) : (
          <span className="vivo__vazio-cel">—</span>
        )}
      </td>

      {/* FINANÇAS */}
      <td className="vivo__cel">
        {income > 0 ? (
          <div className="vivo__fin-lista">
            <div className="vivo__fin-item vivo__verde">
              <span>₣</span><span>{income.toFixed(0)}</span>
            </div>
            <div className="vivo__fin-item vivo__vermelho">
              <span>₣</span><span>{expense.toFixed(0)}</span>
            </div>
            {fines > 0 && (
              <div className="vivo__fin-item vivo__vermelho">
                <span>₣</span><span>{fines.toFixed(0)}</span>
              </div>
            )}
          </div>
        ) : (
          <span className="vivo__vazio-cel">—</span>
        )}
      </td>

      {/* VELOCIDADE */}
      <td className="vivo__cel vivo__cel--vel">
        <div className={'vivo__velcirc' + (acima ? ' is-acima' : '')}>
          <span className="vivo__vel-num">{vel.toFixed(0)}</span>
          <span className="vivo__vel-lim">{limVel > 0 ? limVel : '—'}</span>
        </div>
      </td>
    </tr>
  );
}

// ---------------------------------------------------------------------------

export default function AoVivo() {
  const [snapshot, setSnapshot] = useState<SnapshotResponse | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    async function buscar() {
      try {
        const dados = await api.get<SnapshotResponse>('/vtlog/live');
        if (ativo) { setSnapshot(dados); setErro(null); }
      } catch {
        if (ativo) setErro('Erro ao buscar dados ao vivo.');
      }
    }
    buscar();
    const t = setInterval(buscar, 5_000);
    return () => { ativo = false; clearInterval(t); };
  }, []);

  const motoristas = snapshot?.snapshot ? parsearMotoristas(snapshot.snapshot) : [];
  const online = snapshot?.online && motoristas.length > 0;

  return (
    <div className="vivo">
      <header className="vivo__head">
        <div className="vivo__head-left">
          <span className={'vivo__dot vivo__dot--head' + (online ? ' is-online' : '')} />
          <h1>AO VIVO</h1>
        </div>
        <div className="vivo__head-right">
          <span className="vivo__head-subtitulo">TELEMETRIA EM TEMPO REAL DE MOTORISTAS CONECTADOS</span>
          {snapshot?.atualizado && (
            <span className="vivo__atualizado">
              {new Date(snapshot.atualizado).toLocaleTimeString('pt-BR')}
            </span>
          )}
        </div>
      </header>

      {erro && <div className="vivo__erro">{erro}</div>}

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
              {motoristas.map((m, i) => (
                <LinhaMotorista key={m.steamID ?? m.steam_id ?? i} m={m} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="vivo__vazio">
          <p>Nenhum motorista online agora.</p>
          <p className="vivo__vazio-sub">Os dados aparecem aqui quando o plugin VTLog estiver ativo.</p>
        </div>
      )}
    </div>
  );
}
