import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';

/** Busca dados da API cuidando de loading e erro — evita repetir isso em toda tela. */
export function useApi<T>(caminho: string | null) {
  const [dados, setDados] = useState<T | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const buscar = useCallback(async () => {
    if (!caminho) { setCarregando(false); return; }
    setCarregando(true);
    setErro(null);
    try {
      setDados(await api.get<T>(caminho));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Erro inesperado.');
    } finally {
      setCarregando(false);
    }
  }, [caminho]);

  useEffect(() => { buscar(); }, [buscar]);

  return { dados, carregando, erro, recarregar: buscar };
}
