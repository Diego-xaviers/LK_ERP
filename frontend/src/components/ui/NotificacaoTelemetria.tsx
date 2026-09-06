import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { useSessao } from '../../auth';
import './NotificacaoTelemetria.css';

interface AcaoTelemetria {
  tipo: 'VIAGEM_CRIADA' | 'ENTREGA_CONCLUIDA';
  numero: number;
}

function parsearAcao(raw: string | null | undefined): AcaoTelemetria | null {
  if (!raw) return null;
  const [tipo, num] = raw.split(':');
  const numero = parseInt(num, 10);
  if (!tipo || isNaN(numero)) return null;
  return { tipo: tipo as AcaoTelemetria['tipo'], numero };
}

export default function NotificacaoTelemetria() {
  const { usuario } = useSessao();
  const navigate = useNavigate();
  const [acao, setAcao] = useState<AcaoTelemetria | null>(null);
  const vistoPor = useRef<string | null>(null);

  useEffect(() => {
    let ativo = true;

    async function checar() {
      try {
        const dados = await api.get<{ acaoPendente?: string }>(`/telemetria/atual/${usuario.id}`);
        if (!ativo) return;
        const nova = parsearAcao(dados?.acaoPendente);
        if (nova) {
          const chave = `${nova.tipo}:${nova.numero}`;
          if (chave !== vistoPor.current) {
            vistoPor.current = chave;
            setAcao(nova);
          }
        }
      } catch {
        // silencioso: motorista pode não ter sessão de telemetria
      }
    }

    checar();
    const t = setInterval(checar, 5_000);
    return () => { ativo = false; clearInterval(t); };
  }, [usuario.id]);

  if (!acao) return null;

  const fechar = () => setAcao(null);

  if (acao.tipo === 'VIAGEM_CRIADA') {
    return (
      <div className="ntelem ntelem--verde">
        <span className="ntelem__icone">🚛</span>
        <span className="ntelem__texto">
          Viagem <strong>#{acao.numero}</strong> criada automaticamente — documentos gerados!
        </span>
        <button className="ntelem__btn" onClick={() => { navigate('/viagem'); fechar(); }}>
          Ver viagem
        </button>
        <button className="ntelem__fechar" onClick={fechar}>✕</button>
      </div>
    );
  }

  return (
    <div className="ntelem ntelem--amarelo">
      <span className="ntelem__icone">📦</span>
      <span className="ntelem__texto">
        Entrega da viagem <strong>#{acao.numero}</strong> registrada — vá ao escritório entregar os documentos!
      </span>
      <button className="ntelem__btn" onClick={() => { navigate('/historico'); fechar(); }}>
        Ver viagem
      </button>
      <button className="ntelem__fechar" onClick={fechar}>✕</button>
    </div>
  );
}
