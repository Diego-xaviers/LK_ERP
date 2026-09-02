import { Client, GatewayIntentBits } from 'discord.js';

const {
  DISCORD_TOKEN,
  VTLOG_CHANNEL_ID,   // ID do canal #registro-vtlog
  LK_API_URL,         // ex.: https://lkerp-production.up.railway.app/api
  VTLOG_SECRET,       // mesmo valor que VTLOG_SECRET no Railway
  VTC_ID = '7777',
} = process.env;

if (!DISCORD_TOKEN || !VTLOG_CHANNEL_ID || !LK_API_URL || !VTLOG_SECRET) {
  console.error('Variáveis obrigatórias: DISCORD_TOKEN, VTLOG_CHANNEL_ID, LK_API_URL, VTLOG_SECRET');
  process.exit(1);
}

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent] });

client.once('ready', async () => {
  console.log(`Bot conectado como ${client.user.tag}`);
  console.log(`Monitorando canal ${VTLOG_CHANNEL_ID}`);
  await varrerHistorico();
});

/**
 * Lê o que já está no canal e registra o que ainda não entrou.
 *
 * Existe porque o bot só enxerga o que chega enquanto está de pé: sem isso,
 * toda entrega feita durante um deploy ou uma queda ficaria perdida. O backend
 * recusa job repetido (409), então repassar o histórico é seguro.
 */
async function varrerHistorico() {
  try {
    const canal = await client.channels.fetch(VTLOG_CHANNEL_ID);
    const mensagens = await canal.messages.fetch({ limit: 50 });
    console.log(`[hist] ${mensagens.size} mensagens recentes no canal`);

    // Da mais antiga para a mais nova, para as viagens saírem na ordem certa.
    const ordenadas = [...mensagens.values()].reverse();
    let achados = 0;

    for (const msg of ordenadas) {
      const texto = textoDaMensagem(msg);
      const jobId = extrairJobId(texto);
      console.log(
        `[hist] autor=${msg.author?.tag ?? '?'} webhook=${!!msg.webhookId} ` +
        `embeds=${msg.embeds?.length ?? 0} chars=${texto.length} job=${jobId ?? '-'}`
      );
      if (jobId) {
        achados++;
        await processarJob(jobId, canal);
      }
    }
    console.log(`[hist] varredura concluída — ${achados} job(s) encontrado(s)`);
  } catch (err) {
    console.error('[hist] falhou:', err.message);
  }
}

client.on('messageCreate', async (msg) => {
  if (msg.channelId !== VTLOG_CHANNEL_ID) return;

  const texto = textoDaMensagem(msg);

  // Toda mensagem do canal é registrada. Sem isso, "não detectou nada" e "não
  // consegue ler nada" produzem o mesmo silêncio nos logs — e são problemas
  // completamente diferentes.
  console.log(
    `[msg] autor=${msg.author?.tag ?? '?'} webhook=${!!msg.webhookId} ` +
    `embeds=${msg.embeds?.length ?? 0} chars=${texto.length}`
  );

  if (texto.length === 0) {
    console.warn(
      '[msg] mensagem chegou vazia (sem texto e sem embeds). Quase sempre é o ' +
      'Message Content Intent desligado no Developer Portal, ou o bot sem ' +
      'permissão de ler o histórico do canal.'
    );
    return;
  }

  const jobId = extrairJobId(texto);
  if (!jobId) {
    console.log(`[msg] nenhum job reconhecido. Trecho: ${texto.slice(0, 300)}`);
    return;
  }

  console.log(`[VTLog] Job detectado: #${jobId}`);
  await processarJob(jobId, msg.channel);
});

/**
 * Junta tudo que dá para ler numa mensagem: conteúdo, e cada pedaço do embed
 * (inclusive os fields, que é onde o VTLog costuma pôr os dados da entrega).
 */
function textoDaMensagem(msg) {
  const partes = [msg.content];
  for (const e of msg.embeds ?? []) {
    partes.push(e.title, e.description, e.url, e.footer?.text, e.author?.name);
    for (const f of e.fields ?? []) partes.push(f.name, f.value);
  }
  return partes.filter(Boolean).join(' \n ');
}

/**
 * Acha o número do job. A URL do VTLog é a pista mais confiável — o texto
 * visível muda com o idioma do servidor, o link não.
 */
function extrairJobId(texto) {
  const porUrl = texto.match(/vtlog\.net\/(?:jobs?|job)\/(\d{3,10})/i);
  if (porUrl) return porUrl[1];

  const match = texto.match(/[Tt]rabalho\s*#?(\d{4,8})|[Jj]ob\s*#?(\d{4,8})|#(\d{4,8})/);
  return match ? (match[1] || match[2] || match[3]) : null;
}

async function processarJob(jobId, canal) {
  try {
    // 1. Busca dados completos na API pública do VTLog
    const jobUrl = `https://api.vtlog.net/v1/jobs/${jobId}`;
    const jobRes = await fetch(jobUrl);
    if (!jobRes.ok) {
      console.warn(`[VTLog] Job ${jobId} não encontrado na API (${jobRes.status})`);
      return;
    }
    // A API alterna entre devolver o job dentro de "data" e devolvê-lo na raiz.
    // Fixar em uma das formas quebra de forma intermitente, então aceita as duas.
    const cru = await jobRes.json();
    const job = cru?.data ?? cru;
    if (!job?.job_id) {
      console.warn(`[VTLog] Job ${jobId} veio em formato inesperado, ignorando.`);
      return;
    }

    // Filtra apenas entregas da nossa VTC
    if (String(job.vtc_id) !== String(VTC_ID)) {
      console.log(`[VTLog] Job ${jobId} é de outra VTC (${job.vtc_id}), ignorando.`);
      return;
    }

    // Job cancelado não é entrega: viraria uma viagem que nunca aconteceu.
    if (job.job_status !== 'delivered') {
      console.log(`[VTLog] Job ${jobId} está "${job.job_status}", ignorando.`);
      return;
    }

    // Monta payload para o backend
    const payload = {
      job_id: String(jobId),
      steam_id: String(job.steam_id),
      origem: job.departure_city_name || 'Desconhecido',
      destino: job.arrival_city_name || 'Desconhecido',
      empresa_origem: job.departure_company_name || 'Desconhecido',
      empresa_destino: job.arrival_company_name || 'Desconhecido',
      carga: job.cargo_name || 'Carga desconhecida',
      peso_kg: num(job.cargo_mass),
      distancia_km: num(job.distance_client),
      combustivel_gasto_l: num(job.fuel_used),
      dano_pct: calcularDano(job),
      valor_frete: num(job.income),
    };

    console.log(`[VTLog] Enviando job ${jobId} para o backend...`, JSON.stringify(payload));

    // 2. Envia para o backend LK
    const lkRes = await fetch(`${LK_API_URL}/vtlog/entrega`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Vtlog-Secret': VTLOG_SECRET,
      },
      body: JSON.stringify(payload),
    });

    const resposta = await lkRes.json().catch(() => ({}));

    if (lkRes.ok) {
      console.log(`[VTLog] Job ${jobId} registrado: viagem #${resposta.viagem}`);
    } else if (lkRes.status === 409) {
      console.log(`[VTLog] Job ${jobId} já registrado anteriormente.`);
    } else {
      console.error(`[VTLog] Erro ao registrar job ${jobId}: ${JSON.stringify(resposta)}`);
      // Avisa no canal se o motorista não tem Steam ID configurado
      if (resposta.erro && resposta.erro.includes('Steam ID')) {
        await canal.send(
          `⚠️ Entrega do job #${jobId} detectada, mas o motorista (Steam ID \`${job.steam_id}\`) não está cadastrado no sistema LK. Configure o Steam ID no perfil.`
        ).catch(() => {});
      }
    }
  } catch (err) {
    console.error(`[VTLog] Erro ao processar job ${jobId}:`, err.message);
  }
}

/** Converte os números da API, que vêm ora como número, ora como string. */
function num(v) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

/**
 * Pior dano da viagem, em %.
 *
 * É o maior entre os componentes, não a média: o que interessa ao gestor é se
 * alguma coisa chegou detonada. Uma média dilui um motor a 40% no meio de sete
 * zeros e a viagem passaria como se nada tivesse acontecido.
 */
function calcularDano(job) {
  const campos = [
    job.truck_cabin_damage, job.truck_chassis_damage, job.truck_engine_damage,
    job.truck_transmission_damage, job.truck_wheels_damage,
    job.trailers_chassis_damage, job.trailers_wheels_damage,
    job.trailers_body_damage, job.cargo_damage, job.trailer_cargo_damage,
  ].map(num).filter((v) => v !== null);

  if (campos.length === 0) return null;
  return Math.round(Math.max(...campos) * 100) / 100;
}

client.login(DISCORD_TOKEN);
