import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { api } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useUsuario } from '../auth';
import { TelemetriaAtual, TelemetriaViagem, Viagem } from '../api/tipos';
import Icon from '../components/ui/Icon';
import './AoVivo.css';

// ---------------------------------------------------------------------------
// Tipos VTLog
// ---------------------------------------------------------------------------

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
  x?: number; y?: number; z?: number;
  X?: number; Y?: number; Z?: number;
  position?: { x?: number; y?: number; z?: number };
  truck?: { x?: number; y?: number; z?: number };
  [key: string]: unknown;
}

interface SnapshotResponse {
  online: boolean;
  atualizado: string | null;
  snapshot?: string;
}

// ---------------------------------------------------------------------------
// Utilitários
// ---------------------------------------------------------------------------

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

function coordenadas(m: MotoristaVivo): { x: number; z: number } | null {
  const x = m.x ?? m.X ?? m.position?.x ?? m.truck?.x;
  const z = m.z ?? m.Z ?? m.position?.z ?? m.truck?.z;
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

function fmt(v: number | null | undefined, casas: number): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '—';
  return v.toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
}

// ---------------------------------------------------------------------------
// Mapa Leaflet
// ---------------------------------------------------------------------------

const ETS2_BOUNDS: L.LatLngBoundsLiteral = [[-40000, -20000], [10000, 30000]];

function MapaAoVivo({ motoristas }: { motoristas: MotoristaVivo[] }) {
  const divRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const marcadoresRef = useRef<Map<string, L.CircleMarker>>(new Map());

  useEffect(() => {
    if (!divRef.current || mapRef.current) return;
    const map = L.map(divRef.current, {
      crs: L.CRS.Simple, minZoom: -4, maxZoom: 2,
      zoomControl: true, attributionControl: false,
    });
    L.tileLayer('https://cdn.truckersmp.com/images/mods/ets2/{z}/{x}/{y}.png', {
      tileSize: 256, noWrap: true, errorTileUrl: '',
    }).addTo(map);
    map.fitBounds(ETS2_BOUNDS);
    mapRef.current = map;
    return () => { map.remove(); mapRef.current = null; };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const ids = new Set<string>();
    for (const m of motoristas) {
      const coords = coordenadas(m);
      const id = m.steamID ?? m.steam_id ?? m.username ?? String(Math.random());
      ids.add(id);
      if (!coords) continue;
      const latlng: L.LatLngExpression = [-coords.z, coords.x];
      const vel = num(m.speed);
      const cor = vel > (num(m.speedLimit) || 90) ? '#ef4444' : '#22c55e';
      const tooltip = `<strong>${nomeMotorista(m)}</strong><br>${vel} km/h · ${m.citySource ?? '?'} → ${m.cityDest ?? '?'}`;
      const existente = marcadoresRef.current.get(id);
      if (existente) {
        existente.setLatLng(latlng);
        existente.setStyle({ color: cor, fillColor: cor });
        existente.bindTooltip(tooltip);
      } else {
        const marker = L.circleMarker(latlng, {
          radius: 8, color: cor, fillColor: cor, fillOpacity: 0.9, weight: 2,
        }).addTo(map);
        marker.bindTooltip(tooltip);
        marcadoresRef.current.set(id, marker);
      }
    }
    for (const [id, marker] of marcadoresRef.current) {
      if (!ids.has(id)) { marker.remove(); marcadoresRef.current.delete(id); }
    }
  }, [motoristas]);

  const semCoordenadas = motoristas.every((m) => !coordenadas(m));
  return (
    <div className="vivo__mapa-wrap">
      <div ref={divRef} className="vivo__mapa" />
      {semCoordenadas && motoristas.length > 0 && (
        <div className="vivo__mapa-overlay">
          <p>Coordenadas não disponíveis no snapshot do VTLog.</p>
          <p className="vivo__mapa-sub">A tabela abaixo mostra os dados recebidos.</p>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cockpit pessoal (mostra quando o agente do usuário está online)
// ---------------------------------------------------------------------------

function CockpitPessoal({ t }: { t: TelemetriaAtual }) {
  const tanquePct = t.combustivelL && t.combustivelCapacidadeL
    ? (t.combustivelL / t.combustivelCapacidadeL) * 100 : null;
  const danoPior = Math.max(
    t.danoMotorPct ?? 0, t.danoCambioPct ?? 0, t.danoCabinePct ?? 0,
    t.danoChassiPct ?? 0, t.danoRodasPct ?? 0,
  );

  return (
    <section className="vivo__cockpit">
      <div className="vivo__cockpit-head">
        <span className="vivo__cockpit-titulo">
          <span className="vivo__dot" /> Você está online
        </span>
        {t.modeloCaminhao && (
          <span className="vivo__cockpit-sub">{t.modeloCaminhao}{t.placaCaminhao ? ` · ${t.placaCaminhao}` : ''}</span>
        )}
      </div>
      <div className="vivo__cockpit-dials">
        <Dial titulo="Velocidade" valor={fmt(t.velocidadeKmh, 0)} unidade="km/h" icon="gauge" />
        <Dial titulo="Combustível" valor={fmt(t.combustivelL, 0)} unidade="L"
              barra={tanquePct} alerta={tanquePct !== null && tanquePct < 15} icon="fuel" />
        <Dial titulo="Dano" valor={fmt(danoPior, 1)} unidade="%"
              barra={danoPior} alerta={danoPior > 20} icon="alertCircle" />
        <Dial titulo="Odômetro" valor={fmt(t.odometroKm, 0)} unidade="km" icon="route" />
      </div>
      {(t.cidadeOrigem || t.cargaNome) && (
        <div className="vivo__cockpit-rota">
          {t.cargaNome && <span className="vivo__cockpit-carga">📦 {t.cargaNome}</span>}
          {t.cidadeOrigem && t.cidadeDestino && (
            <span className="vivo__cockpit-trecho">{t.cidadeOrigem} → {t.cidadeDestino}</span>
          )}
        </div>
      )}
    </section>
  );
}

function Dial({ titulo, valor, unidade, icon, barra, alerta }: {
  titulo: string; valor: string; unidade: string;
  icon: 'gauge' | 'fuel' | 'alertCircle' | 'route';
  barra?: number | null; alerta?: boolean;
}) {
  return (
    <div className={'vivo__dial' + (alerta ? ' is-alerta' : '')}>
      <span className="vivo__dial-titulo"><Icon name={icon} size={13} /> {titulo}</span>
      <strong>{valor}<small>{unidade}</small></strong>
      {barra !== null && barra !== undefined && (
        <div className="vivo__dial-barra"><i style={{ width: `${Math.min(100, Math.max(0, barra))}%` }} /></div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Linha da tabela VTLog
// ---------------------------------------------------------------------------

function LinhaMotorista({ m }: { m: MotoristaVivo }) {
  const vel = num(m.speed);
  const limVel = num(m.speedLimit);
  const acima = vel > limVel && limVel > 0;

  const combustivelAtual = num(m.fuelCurrent);
  const combustivelTotal = num(m.fuelCapacity);
  const combustivelPct = pct(combustivelAtual, combustivelTotal);

  const percorrido = num(m.distance);
  const restante = num(m.routeDistance);
  const totalKm = percorrido + restante;
  const progressoPct = pct(percorrido, totalKm);

  const danoCabine = num(m.damageCabin);
  const danoReboque = num(m.damageTrailer);
  const danoPior = Math.max(danoCabine, danoReboque, num(m.damageCargo));

  return (
    <tr className="vivo__linha">
      {/* MOTORISTA */}
      <td className="vivo__cel vivo__cel--motorista">
        <span className="vivo__dot vivo__dot--sm" />
        <div>
          <span className="vivo__nome">{nomeMotorista(m)}</span>
          <span className="vivo__sub">{m.game ?? 'ETS2'}</span>
        </div>
      </td>

      {/* DANO */}
      <td className="vivo__cel">
        <span className={'vivo__badge' + (danoPior > 20 ? ' vivo__badge--vermelho' : danoPior > 5 ? ' vivo__badge--amarelo' : ' vivo__badge--verde')}>
          {danoPior.toFixed(0)}%
        </span>
        <span className="vivo__sub">cab {danoCabine.toFixed(0)}% · reb {danoReboque.toFixed(0)}%</span>
      </td>

      {/* CARGA */}
      <td className="vivo__cel">
        {m.cargoName
          ? <><span className="vivo__cel-val">{m.cargoName}</span><span className="vivo__sub">{num(m.cargoMass) > 0 ? `${num(m.cargoMass).toFixed(0)} T` : ''}</span></>
          : <span className="vivo__sub">—</span>}
      </td>

      {/* COMBUSTÍVEL */}
      <td className="vivo__cel">
        <div className="vivo__barra-row">
          <span className="vivo__cel-val">{combustivelPct}%</span>
          <div className="vivo__barra-wrap">
            <div className="vivo__barra" style={{
              width: `${combustivelPct}%`,
              background: combustivelPct > 30 ? '#f5a623' : '#e74c3c',
            }} />
          </div>
        </div>
        <span className="vivo__sub">{combustivelAtual.toFixed(0)} L · {num(m.fuelRange).toFixed(0)} km</span>
      </td>

      {/* PROGRESSO */}
      <td className="vivo__cel">
        {totalKm > 0 ? (
          <>
            <div className="vivo__barra-row">
              <span className="vivo__cel-val">{progressoPct}%</span>
              <div className="vivo__barra-wrap">
                <div className="vivo__barra" style={{ width: `${progressoPct}%`, background: '#3b82f6' }} />
              </div>
            </div>
            <span className="vivo__sub">
              {m.citySource && m.cityDest ? `${m.citySource} → ${m.cityDest}` : `${percorrido.toFixed(0)} / ${totalKm.toFixed(0)} km`}
            </span>
          </>
        ) : <span className="vivo__sub">—</span>}
      </td>

      {/* FINANÇAS */}
      <td className="vivo__cel">
        {num(m.income) > 0
          ? <><span className="vivo__cel-val vivo__verde">₣ {num(m.income).toFixed(0)}</span><span className="vivo__sub vivo__vermelho">₣ {num(m.expense).toFixed(0)}</span></>
          : <span className="vivo__sub">—</span>}
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
// Página principal
// ---------------------------------------------------------------------------

export default function AoVivo() {
  const usuario = useUsuario();
  const [snapshot, setSnapshot] = useState<SnapshotResponse | null>(null);
  const [erroVtlog, setErroVtlog] = useState<string | null>(null);

  // Telemetria pessoal
  const { dados: telePessoal, recarregar: recarregarTele } = useApi<TelemetriaAtual>(`/telemetria/atual/${usuario.id}`);
  const { dados: viagemAtiva } = useApi<Viagem>(`/viagens/ativa/${usuario.id}`);
  const agenteOnline = telePessoal?.online === true;

  // Poll telemetria pessoal a cada 3s quando online
  useEffect(() => {
    if (!agenteOnline) return;
    const t = setInterval(recarregarTele, 3_000);
    return () => clearInterval(t);
  }, [agenteOnline, recarregarTele]);

  // VTLog snapshot polling
  useEffect(() => {
    let ativo = true;
    async function buscar() {
      try {
        const dados = await api.get<SnapshotResponse>('/vtlog/live');
        if (ativo) { setSnapshot(dados); setErroVtlog(null); }
      } catch {
        if (ativo) setErroVtlog('Erro ao buscar dados VTLog.');
      }
    }
    buscar();
    const t = setInterval(buscar, 5_000);
    return () => { ativo = false; clearInterval(t); };
  }, []);

  const motoristas = snapshot?.snapshot ? parsearMotoristas(snapshot.snapshot) : [];
  const vtlogOnline = snapshot?.online && motoristas.length > 0;

  return (
    <div className="vivo">
      <header className="vivo__head">
        <div>
          <h1>Ao Vivo</h1>
          <p>Telemetria em tempo real — você e todos os motoristas online</p>
        </div>
        <div className="vivo__head-right">
          <span className={'vivo__status' + (agenteOnline ? ' is-online' : '')}>
            <span className="vivo__pulse" />
            {agenteOnline ? 'Seu agente online' : 'Seu agente offline'}
          </span>
          {vtlogOnline && (
            <span className="vivo__status is-online" style={{ marginTop: 4 }}>
              <span className="vivo__pulse" />
              {motoristas.length} no VTLog
            </span>
          )}
          {snapshot?.atualizado && (
            <span className="vivo__atualizado">
              VTLog {new Date(snapshot.atualizado).toLocaleTimeString('pt-BR')}
            </span>
          )}
        </div>
      </header>

      {/* Cockpit pessoal */}
      {agenteOnline && telePessoal && <CockpitPessoal t={telePessoal} />}

      {erroVtlog && <div className="vivo__erro">{erroVtlog}</div>}

      {/* Pills — quem está online no VTLog */}
      {motoristas.length > 0 && (
        <div className="vivo__bot-barra">
          <span className="vivo__bot-label">Online:</span>
          {motoristas.map((m, i) => (
            <span key={m.steamID ?? i} className="vivo__bot-pill">
              🟢 {nomeMotorista(m)}
            </span>
          ))}
        </div>
      )}

      {/* Mapa */}
      <MapaAoVivo motoristas={motoristas} />

      {/* Tabela de motoristas */}
      {vtlogOnline ? (
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
                <LinhaMotorista key={m.steamID ?? i} m={m} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="vivo__vazio">
          <p>Nenhum motorista online no VTLog agora.</p>
          <p className="vivo__vazio-sub">Os dados aparecem aqui quando o plugin VTLog estiver ativo.</p>
        </div>
      )}

      {/* Conferência da viagem ativa (apenas quando agente pessoal online) */}
      {agenteOnline && viagemAtiva && <ConferenciaViagemInline viagemId={viagemAtiva.id} numero={viagemAtiva.numero} />}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Bloco de conferência de viagem (mantido em AoVivo para quem está online)
// ---------------------------------------------------------------------------

function ConferenciaViagemInline({ viagemId, numero }: { viagemId: string; numero: number }) {
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
    <section className="vivo__conferencia">
      <h2>Conferência da viagem #{numero}</h2>
      <div className="vivo__conf-grid">
        <div className="vivo__conf-item"><span>Rodado</span><strong>{fmt(distancia, 1)} km</strong></div>
        <div className="vivo__conf-item"><span>Combustível gasto</span><strong>{fmt(gasto, 1)} L</strong></div>
        <div className="vivo__conf-item"><span>Abastecido</span><strong>{fmt(dados.litrosAbastecidos, 1)} L</strong></div>
        <div className="vivo__conf-item"><span>Dano na viagem</span><strong className={((dano ?? 0) > 5) ? 'vivo__vermelho' : ''}>{fmt(dano, 1)}%</strong></div>
      </div>
      {sinais.length > 0 && (
        <div className="vivo__sinais">
          <strong><Icon name="shield" size={14} /> Atenção</strong>
          <ul>{sinais.map((s) => <li key={s}>{s}</li>)}</ul>
        </div>
      )}
    </section>
  );
}
