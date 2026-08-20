export const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api';

const CHAVE_TOKEN = 'lk.token';

export const sessao = {
  token: () => localStorage.getItem(CHAVE_TOKEN),
  guardar: (t: string) => localStorage.setItem(CHAVE_TOKEN, t),
  limpar: () => localStorage.removeItem(CHAVE_TOKEN),
};

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(caminho: string, init?: RequestInit): Promise<T> {
  let resposta: Response;
  const token = sessao.token();
  try {
    resposta = await fetch(BASE + caminho, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    });
  } catch {
    // Erro de rede — quase sempre é o backend fora do ar
    throw new ApiError(0, 'Não foi possível falar com o servidor. O backend está rodando?');
  }

  if (resposta.status === 204) return undefined as T;

  // Token vencido ou inválido: derruba a sessão e volta pro login.
  if (resposta.status === 401 && !caminho.startsWith('/auth/')) {
    sessao.limpar();
    window.location.reload();
    throw new ApiError(401, 'Sessão expirada.');
  }

  const texto = await resposta.text();
  const corpo = texto ? JSON.parse(texto) : null;

  if (!resposta.ok) {
    throw new ApiError(resposta.status, corpo?.erro ?? 'Erro inesperado no servidor.');
  }
  return corpo as T;
}

export const api = {
  get: <T>(c: string) => request<T>(c),
  post: <T>(c: string, corpo?: unknown) =>
    request<T>(c, { method: 'POST', body: corpo ? JSON.stringify(corpo) : undefined }),
  put: <T>(c: string, corpo?: unknown) =>
    request<T>(c, { method: 'PUT', body: corpo ? JSON.stringify(corpo) : undefined }),
  delete: <T>(c: string) => request<T>(c, { method: 'DELETE' }),
};
