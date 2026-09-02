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

client.once('ready', () => {
  console.log(`Bot conectado como ${client.user.tag}`);
  console.log(`Monitorando canal ${VTLOG_CHANNEL_ID}`);
});

client.on('messageCreate', async (msg) => {
  if (msg.channelId !== VTLOG_CHANNEL_ID) return;

  // VTLog posta via webhook/app — filtra por embeds
  if (!msg.embeds || msg.embeds.length === 0) return;

  for (const embed of msg.embeds) {
    const jobId = extrairJobId(embed);
    if (!jobId) continue;

    console.log(`[VTLog] Job detectado: #${jobId}`);
    await processarJob(jobId, msg.channel);
  }
});

/**
 * Extrai o job_id do embed do VTLog.
 * O título costuma ser: "Entregue • Trabalho #123456"
 */
function extrairJobId(embed) {
  const textos = [embed.title, embed.description, embed.footer?.text].filter(Boolean).join(' ');
  const match = textos.match(/[Tt]rabalho\s*#?(\d{4,8})|[Jj]ob\s*#?(\d{4,8})|#(\d{4,8})/);
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
    const job = await jobRes.json();

    // Filtra apenas entregas da nossa VTC
    if (String(job.vtc_id) !== String(VTC_ID)) {
      console.log(`[VTLog] Job ${jobId} é de outra VTC (${job.vtc_id}), ignorando.`);
      return;
    }

    // Monta payload para o backend
    const payload = {
      job_id: String(jobId),
      steam_id: String(job.steam_id),
      origem: job.source_city || job.start_city || 'Desconhecido',
      destino: job.destination_city || job.end_city || 'Desconhecido',
      empresa_origem: job.source_company || job.cargo_company || 'Desconhecido',
      empresa_destino: job.destination_company || 'Desconhecido',
      carga: job.cargo || 'Carga desconhecida',
      peso_kg: job.cargo_mass ? Number(job.cargo_mass) : null,
      distancia_km: job.distance_client ? Number(job.distance_client) : null,
      combustivel_gasto_l: job.fuel_used ? Number(job.fuel_used) : null,
      dano_pct: calcularDano(job),
      valor_frete: job.revenue ? Number(job.revenue) : null,
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

/** Soma os danos do veículo retornados pela API do VTLog. */
function calcularDano(job) {
  const campos = [
    job.damage_chassis, job.damage_engine, job.damage_transmission,
    job.damage_cabin, job.damage_wheels, job.damage_cargo,
  ];
  const soma = campos.reduce((acc, v) => acc + (v ? Number(v) : 0), 0);
  const count = campos.filter((v) => v !== undefined && v !== null).length;
  return count > 0 ? Math.round((soma / count) * 100) / 100 : null;
}

client.login(DISCORD_TOKEN);
