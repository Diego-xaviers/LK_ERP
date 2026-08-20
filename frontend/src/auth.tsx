import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, ApiError, sessao } from './api/client';
import { Usuario } from './api/tipos';
import { Carregando } from './components/ui/Estado';
import Login from './pages/Login';

/**
 * Sessão do usuário logado.
 *
 * O usuário vem do token JWT guardado no navegador — antes vinha de
 * /usuarios/atual com o e-mail na URL, o que deixava qualquer um se passar
 * por qualquer outro.
 */
interface Sessao {
  usuario: Usuario;
  eGestor: boolean;
  sair: () => void;
}

const SessaoContext = createContext<Sessao | null>(null);

export function useUsuario() {
  const s = useContext(SessaoContext);
  if (!s) throw new Error('useUsuario precisa estar dentro de <ProvedorUsuario>');
  return s.usuario;
}

export function useSessao() {
  const s = useContext(SessaoContext);
  if (!s) throw new Error('useSessao precisa estar dentro de <ProvedorUsuario>');
  return s;
}

export function ProvedorUsuario({ children }: { children: React.ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);

  const carregar = useCallback(async () => {
    if (!sessao.token()) { setUsuario(null); setCarregando(false); return; }
    try {
      setUsuario(await api.get<Usuario>('/usuarios/atual'));
    } catch (e) {
      // Token velho ou backend fora: cai pro login em vez de travar a tela.
      if (e instanceof ApiError && e.status !== 0) sessao.limpar();
      setUsuario(null);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => { carregar(); }, [carregar]);

  function sair() {
    sessao.limpar();
    setUsuario(null);
  }

  if (carregando) return <Carregando texto="Conectando ao servidor..." />;
  if (!usuario) return <Login aoEntrar={() => { setCarregando(true); carregar(); }} />;

  return (
    <SessaoContext.Provider value={{ usuario, eGestor: usuario.papel === 'GESTOR', sair }}>
      {children}
    </SessaoContext.Provider>
  );
}
