import { useEffect, useState } from 'react';
import { api } from '../api/client';
import './AoVivo.css';

interface MotoristaVivo {
  steamID?: string;
  username?: string;
  // Campos documentados da API VTLog v2
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
  // Campos obfuscados do Socket.IO (fallback)
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

function formatarTempo(segundos: number): string {
  if (!segundos) return '--';
  const h = Math.floor(segundos / 3600);
  const m = Math.floor((segundos % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

function BarraCombustivel({ atual, total }: { atual: number; total: number }) {
  const p = pct(atual, total);
  const cor = p > 30 ? '#f5a623' : '#e74c3c';
  return (
    <div className="vivo__barra-wrap">
      <div className="vivo__barra" style={{ width: `${p}%`, background: cor }} />
    </div>
  );
}

function BarraProgresso({ percorrido, restante }: { percorrido: number; restante: number }) {
  const total = percorrido + restante;
  const p = pct(percorrido, total);
  return (
    <div className="vivo__barra-wrap">
      <div className="vivo__barra" style={{ width: `${p}%`, background: '#f5a623' }} />
    </div>
  );
}

function CartaoMotorista({ m }: { m: MotoristaVivo }) {
  const velocidade = num(m.speed);
  const limiteVel = num(m.speedLimit);
  const combustivelAtual = num(m.fuelCurrent);
  const combustivelTotal = num(m.fuelCapacity);
  const combustivelPct = pct(combustivelAtual, combustivelTotal);
  const percorrido = num(m.distance);
  const restante = num(m.routeDistance);
  const totalKm = percorrido + restante;
  const progressoPct = pct(percorrido, totalKm);
  const danoCabine = num(m.damageСabin ?? m.damageСabin);
  const danoTrailer = num(m.damageTrailer);
  const danoCarga = num(m.damageCargo);

  return (
    <div className="vivo__card">
      {/* Motorista */}
      <div className="vivo__card-header">
        <span className="vivo__dot" />
        <div>
          <div className="vivo__nome">{m.username ?? m.steamID ?? '—'}</div>
          <div className="vivo__sub">{m.game ?? 'ETS2'}</div>
        </div>
      </div>

      {/* Dano */}
      <div className="vivo__bloco">
        <div className="vivo__label">DANO</div>
        <div className="vivo__dano-linha"><span>🚛</span><span>{danoCabine.toFixed(0)}%</span></div>
        <div className="vivo__dano-linha"><span>🚚</span><span>{danoTrailer.toFixed(0)}%</span></div>
        <div className="vivo__dano-linha"><span>📦</span><span>{danoCarga.toFixed(0)}%</span></div>
      </div>

      {/* Carga */}
      {(m.cargoName || num(m.cargoMass) > 0) && (
        <div className="vivo__bloco">
          <div className="vivo__label">CARGA</div>
          {m.cargoName && <div className="vivo__valor-sm">{m.cargoName}</div>}
          {m.cargoCompany && <div className="vivo__sub">{m.cargoCompany}</div>}
          {num(m.cargoMass) > 0 && <div className="vivo__sub">{num(m.cargoMass).toFixed(0)} T</div>}
        </div>
      )}

      {/* Combustível */}
      <div className="vivo__bloco">
        <div className="vivo__label-row">
          <span>⛽ {m.fuelType ?? 'DIESEL'}</span>
          <span className="vivo__pct">{combustivelPct}%</span>
        </div>
        <BarraCombustivel atual={combustivelAtual} total={combustivelTotal} />
        <div className="vivo__barra-foot">
          <span>{combustivelAtual.toFixed(0)} L</span>
          <span>Alcance {num(m.fuelRange).toFixed(0)} km</span>
        </div>
      </div>

      {/* Progresso */}
      {totalKm > 0 && (
        <div className="vivo__bloco">
          <div className="vivo__label-row">
            <span>📍 PROGRESSO</span>
            <span className="vivo__pct">{progressoPct}%</span>
          </div>
          <BarraProgresso percorrido={percorrido} restante={restante} />
          <div className="vivo__barra-foot">
            <span>{percorrido.toFixed(0)} / {totalKm.toFixed(0)} km</span>
            {m.eta ? <span>{formatarTempo(num(m.eta))}</span> : null}
          </div>
          {(m.citySource || m.cityDest) && (
            <div className="vivo__rota">
              <span>{m.citySource ?? '—'}</span>
              <span>→</span>
              <span>{m.cityDest ?? '—'}</span>
            </div>
          )}
        </div>
      )}

      {/* Finanças */}
      {num(m.income) > 0 && (
        <div className="vivo__bloco">
          <div className="vivo__label">FINANÇAS</div>
          <div className="vivo__fin-linha verde">₣ {num(m.income).toFixed(0)}</div>
          <div className="vivo__fin-linha vermelho">₣ {num(m.expense).toFixed(0)}</div>
          {num(m.fines) > 0 && <div className="vivo__fin-linha vermelho">₣ {num(m.fines).toFixed(0)}</div>}
        </div>
      )}

      {/* Velocidade */}
      <div className={'vivo__velocidade' + (velocidade > limiteVel && limiteVel > 0 ? ' is-acima' : '')}>
        <span className="vivo__vel-num">{velocidade}</span>
        <span className="vivo__vel-lim">{limiteVel > 0 ? limiteVel : '--'}</span>
      </div>
    </div>
  );
}

function parsearMotoristas(snapshotJson: string): MotoristaVivo[] {
  try {
    const raw = JSON.parse(snapshotJson);
    // O VTLog pode enviar array direto ou dentro de uma chave
    if (Array.isArray(raw)) return raw;
    if (Array.isArray(raw?.drivers)) return raw.drivers;
    if (Array.isArray(raw?.data)) return raw.data;
    if (Array.isArray(raw?.users)) return raw.users;
    // Um único objeto com steamID = um motorista só
    if (raw?.steamID || raw?.username) return [raw];
    return [];
  } catch {
    return [];
  }
}

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
        <div>
          <h1>Ao Vivo</h1>
          <p>Telemetria em tempo real dos motoristas conectados</p>
        </div>
        <span className={'vivo__status' + (online ? ' is-online' : '')}>
          <span className="vivo__pulse" />
          {online ? `${motoristas.length} motorista(s) online` : 'Nenhum motorista online'}
        </span>
      </header>

      {erro && <div className="vivo__erro">{erro}</div>}

      {online ? (
        <div className="vivo__grid">
          {motoristas.map((m, i) => (
            <CartaoMotorista key={m.steamID ?? i} m={m} />
          ))}
        </div>
      ) : (
        <div className="vivo__vazio">
          <p>Aguardando motoristas entrarem no jogo com o DLL do VTLog instalado.</p>
          {snapshot?.atualizado && (
            <p className="vivo__vazio-sub">
              Última atualização: {new Date(snapshot.atualizado).toLocaleTimeString('pt-BR')}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
