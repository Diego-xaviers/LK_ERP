import { useState } from 'react';
import { api, ApiError, sessao } from '../api/client';
import './Login.css';

interface RespostaLogin { token: string; nome: string; papel: string }

export default function Login({ aoEntrar }: { aoEntrar: () => void }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [entrando, setEntrando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function submeter(e: React.FormEvent) {
    e.preventDefault();
    setEntrando(true);
    setErro(null);
    try {
      const r = await api.post<RespostaLogin>('/auth/login', { email, senha });
      sessao.guardar(r.token);
      aoEntrar();
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível entrar.');
      setEntrando(false);
    }
  }

  return (
    <div className="login">
      <form className="login__cartao" onSubmit={submeter}>
        <div className="login__marca">
          <div className="login__logo">LK</div>
          <div>
            <strong>LK Transportes</strong>
            <span>Painel Logístico</span>
          </div>
        </div>

        <label className="campo">
          <span>E-mail</span>
          <input type="email" value={email} autoFocus autoComplete="username"
                 onChange={(e) => setEmail(e.target.value)} required />
        </label>

        <label className="campo">
          <span>Senha</span>
          <input type="password" value={senha} autoComplete="current-password"
                 onChange={(e) => setSenha(e.target.value)} required />
        </label>

        {erro && <div className="login__erro">{erro}</div>}

        <button className="btn login__entrar" type="submit" disabled={entrando}>
          {entrando ? 'Entrando...' : 'Entrar'}
        </button>

        <p className="login__nota">
          Ainda não tem acesso? O cadastro é liberado por um gestor.
        </p>
      </form>
    </div>
  );
}
