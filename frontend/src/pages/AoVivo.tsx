import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
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
  // Posição no mundo ETS2/ATS (em metros relativos à origem do mapa)
  x?: number;
  y?: number;
  z?: number;
  X?: number;
  Y?: number;
  Z?: number;
  position?: { x?: number; y?: number; z?: number };
  truck?: { x?: number; y?: number; z?: number };
  // Campos variáveis do VTLog
  [key: string]: unknown;
}

interface SnapshotResponse {
  online: boolean;
  atualizado: string | null;
  snapshot?: string;
}

// ---------------------------------------------------------------------------

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

function nomeMotorista(m: MotoristaVivo): string {
  return m.username ?? m.steamID ?? m.steam_id ?? '—';
}

/** Extrai coordenadas x/z do driver no snapshot, tentando vários formatos do VTLog. */
function coordenadas(m: MotoristaVivo): { x: number; z: number } | null {
  const x = m.x ?? m.X ?? (m.position?.x) ?? (m.truck?.x);
  const z = m.z ?? m.Z ?? (m.position?.z) ?? (m.truck?.z);
  if (x != null && z != null && (x !== 0 || z !== 0)) return { x: num(x), z: num(z) };
  return null;
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

// ---------------------------------------------------------------------------
// Mapa ETS2 com Leaflet (CRS.Simple — coordenadas diretas do jogo)
// ---------------------------------------------------------------------------

const ETS2_BOUNDS: L.LatLngBoundsLiteral = [[-40000, -20000], [10000, 30000]];

function MapaAoVivo({ motoristas }: { motoristas: MotoristaVivo[] }) {
  const divRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const marcadoresRef = useRef<Map<string, L.CircleMarker>>(new Map());

  // Inicializa mapa uma vez
  useEffect(() => {
    if (!divRef.current || mapRef.current) return;

    const map = L.map(divRef.current, {
      crs: L.CRS.Simple,
      minZoom: -4,
      maxZoom: 2,
      zoomControl: true,
      attributionControl: false,
    });

    // Tile layer ETS2 — usa o projeto de tiles do TruckersMP
    // Se não carregar, exibe fundo escuro via CSS
    L.tileLayer(
      'https://cdn.truckersmp.com/images/mods/ets2/{z}/{x}/{y}.png',
      { tileSize: 256, noWrap: true, errorTileUrl: '' }
    ).addTo(map);

    map.fitBounds(ETS2_BOUNDS);
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Atualiza marcadores quando motoristas mudam
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const ids = new Set<string>();

    for (const m of motoristas) {
      const coords = coordenadas(m);
      const id = m.steamID ?? m.steam_id ?? m.username ?? String(Math.random());
      ids.add(id);

      if (!coords) continue;

      // ETS2: x → "longitude", z → "latitude" invertida
      const latlng: L.LatLngExpression = [-coords.z, coords.x];
      const velocidade = num(m.speed);
      const cor = velocidade > (num(m.speedLimit) || 90) ? '#ef4444' : '#22c55e';

      const existente = marcadoresRef.current.get(id);
      if (existente) {
        existente.setLatLng(latlng);
        existente.setStyle({ color: cor, fillColor: cor });
        existente.bindTooltip(
          `<strong>${nomeMotorista(m)}</strong><br>${velocidade} km/h · ${m.citySource ?? '?'} → ${m.cityDest ?? '?'}`,
          { permanent: false }
        );
      } else {
        const marker = L.circleMarker(latlng, {
          radius: 8, color: cor, fillColor: cor, fillOpacity: 0.9, weight: 2,
        }).addTo(map);
        marker.bindTooltip(
          `<strong>${nomeMotorista(m)}</strong><br>${velocidade} km/h · ${m.citySource ?? '?'} → ${m.cityDest ?? '?'}`,
          { permanent: false }
        );
        marcadoresRef.current.set(id, marker);
      }
    }

    // Remove marcadores de quem saiu
    for (const [id, marker] of marcadoresRef.current) {
      if (!ids.has(id)) {
        marker.remove();
        marcadoresRef.current.delete(id);
      }
    }
  }, [motoristas]);

  const semCoordenadas = motoristas.every((m) => !coordenadas(m));

  return (
    <div className="vivo__mapa-wrap">
      <div ref={divRef} className="vivo__mapa" />
      {semCoordenadas && motoristas.length > 0 && (
        <div className="vivo__mapa-overlay">
          <p>Coordenadas não disponíveis no snapshot do VTLog.</p>
          <p className="vivo__mapa-sub">Os cards abaixo mostram os dados recebidos.</p>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Card de motorista
// ---------------------------------------------------------------------------

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
  const acimaDaVel = velocidade > limiteVel && limiteVel > 0;

  return (
    <div className="vivo__card">
      <div className="vivo__card-header">
        <span className="vivo__dot" />
        <div>
          <div className="vivo__nome">{nomeMotorista(m)}</div>
          <div className="vivo__sub">{m.game ?? 'ETS2'}</div>
        </div>
        <div className={'vivo__velocidade' + (acimaDaVel ? ' is-acima' : '')}>
          <span className="vivo__vel-num">{velocidade}</span>
          <span className="vivo__vel-lim">{limiteVel > 0 ? limiteVel : '--'}</span>
        </div>
      </div>

      {/* Dano */}
      <div className="vivo__bloco">
        <div className="vivo__label">DANO</div>
        <div className="vivo__dano-linha"><span>🚛</span><span>{num(m.damageCabin).toFixed(0)}%</span></div>
        <div className="vivo__dano-linha"><span>🚚</span><span>{num(m.damageTrailer).toFixed(0)}%</span></div>
        <div className="vivo__dano-linha"><span>📦</span><span>{num(m.damageCargo).toFixed(0)}%</span></div>
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
        <div className="vivo__barra-wrap">
          <div className="vivo__barra" style={{ width: `${combustivelPct}%`, background: combustivelPct > 30 ? '#f5a623' : '#e74c3c' }} />
        </div>
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
          <div className="vivo__barra-wrap">
            <div className="vivo__barra" style={{ width: `${progressoPct}%`, background: '#f5a623' }} />
          </div>
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
    </div>
  );
}

// ---------------------------------------------------------------------------
// Página principal
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
      {/* Cabeçalho */}
      <header className="vivo__head">
        <div>
          <h1>Ao Vivo</h1>
          <p>Telemetria em tempo real dos motoristas conectados</p>
        </div>
        <div className="vivo__head-right">
          <span className={'vivo__status' + (online ? ' is-online' : '')}>
            <span className="vivo__pulse" />
            {online ? `${motoristas.length} online` : 'Nenhum online'}
          </span>
          {snapshot?.atualizado && (
            <span className="vivo__atualizado">
              Atualizado {new Date(snapshot.atualizado).toLocaleTimeString('pt-BR')}
            </span>
          )}
        </div>
      </header>

      {erro && <div className="vivo__erro">{erro}</div>}

      {/* Pills — quem está com o bot ativo */}
      {motoristas.length > 0 && (
        <div className="vivo__bot-barra">
          <span className="vivo__bot-label">Com bot online:</span>
          {motoristas.map((m, i) => (
            <span key={m.steamID ?? i} className="vivo__bot-pill">
              🟢 {nomeMotorista(m)}
            </span>
          ))}
        </div>
      )}

      {/* Mapa */}
      <MapaAoVivo motoristas={motoristas} />

      {/* Cards de motoristas */}
      {online ? (
        <div className="vivo__grid">
          {motoristas.map((m, i) => (
            <CartaoMotorista key={m.steamID ?? i} m={m} />
          ))}
        </div>
      ) : (
        <div className="vivo__vazio">
          <p>Aguardando motoristas entrarem no jogo com o plugin VTLog instalado.</p>
        </div>
      )}
    </div>
  );
}
