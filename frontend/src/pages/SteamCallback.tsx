import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import './Login.css';

export default function SteamCallback() {
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const ok = params.get('ok') === '1';
  const steamId = params.get('steamId');
  const erro = params.get('erro');

  useEffect(() => {
    const t = setTimeout(() => navigate('/perfil', { replace: true }), 2500);
    return () => clearTimeout(t);
  }, [navigate]);

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

        {ok ? (
          <p style={{ textAlign: 'center', fontSize: 'var(--text-sm)', color: 'var(--ink-600)' }}>
            Steam vinculado! ID: <strong>{steamId}</strong>.
            <br />Redirecionando para o perfil...
          </p>
        ) : (
          <>
            <div className="login__erro">
              {erro ? decodeURIComponent(erro) : 'Não foi possível vincular a conta Steam.'}
            </div>
            <button className="btn login__entrar" onClick={() => navigate('/perfil', { replace: true })}>
              Voltar ao perfil
            </button>
          </>
        )}
      </div>
    </div>
  );
}
