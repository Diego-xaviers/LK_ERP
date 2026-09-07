import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { sessao } from '../api/client';
import './Login.css';

export default function DiscordCallback() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [estado, setEstado] = useState<'aguardando' | 'pendente' | 'erro'>('aguardando');
  const [mensagem, setMensagem] = useState('');

  useEffect(() => {
    const token = params.get('token');
    const pendente = params.get('pendente');
    const erro = params.get('erro');

    if (token) {
      sessao.guardar(token);
      navigate('/', { replace: true });
      return;
    }

    if (pendente) {
      const nome = params.get('nome') ?? 'você';
      setMensagem(`Olá, ${nome}! Seu cadastro foi recebido e aguarda aprovação de um gestor.`);
      setEstado('pendente');
      return;
    }

    setMensagem(erro ? decodeURIComponent(erro) : 'Resposta inesperada. Tente de novo.');
    setEstado('erro');
  }, []);

  return (
    <div className="login">
      <div className="login__cartao">
        <div className="login__marca">
          <div className="login__logo">LK</div>
          <div>
            <strong>LK Transportes</strong>
            <span>Painel Logístico</span>
          </div>
        </div>

        {estado === 'aguardando' && <p style={{ textAlign: 'center' }}>Autenticando...</p>}

        {estado === 'pendente' && (
          <>
            <p style={{ textAlign: 'center', fontSize: 'var(--text-sm)', color: 'var(--ink-600)' }}>
              {mensagem}
            </p>
            <p className="login__nota">
              Você receberá acesso assim que a gestão aprovar seu cadastro.
            </p>
          </>
        )}

        {estado === 'erro' && (
          <>
            <div className="login__erro">{mensagem}</div>
            <button className="btn login__entrar" onClick={() => navigate('/', { replace: true })}>
              Voltar ao login
            </button>
          </>
        )}
      </div>
    </div>
  );
}
