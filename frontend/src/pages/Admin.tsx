import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import { Caminhao, Usuario } from '../api/tipos';
import './Admin.css';

type Secao = 'motoristas' | 'caminhoes' | 'carretas' | 'postos' | 'oficinas' | 'empresas' | 'avisos';

interface Campo { nome: string; label: string; tipo?: string; opcoes?: string[]; obrigatorio?: boolean }

type SecaoGenerica = Exclude<Secao, 'motoristas'>;

const CONFIG: Record<SecaoGenerica, {
  label: string; icon: 'truck' | 'layers' | 'fuel' | 'wrench' | 'building' | 'megaphone';
  rota: string; colunas: { chave: string; titulo: string }[]; campos: Campo[];
}> = {
  caminhoes: {
    label: 'Caminhões', icon: 'truck', rota: '/caminhoes',
    colunas: [{ chave: 'placa', titulo: 'Placa' }, { chave: 'marca', titulo: 'Marca' },
              { chave: 'modelo', titulo: 'Modelo' }, { chave: 'identificacaoInterna', titulo: 'Interno' }],
    campos: [{ nome: 'marca', label: 'Marca', obrigatorio: true }, { nome: 'modelo', label: 'Modelo', obrigatorio: true },
             { nome: 'placa', label: 'Placa', obrigatorio: true }, { nome: 'identificacaoInterna', label: 'Identificação interna' }],
  },
  carretas: {
    label: 'Carretas', icon: 'layers', rota: '/carretas',
    colunas: [{ chave: 'placa', titulo: 'Placa' }, { chave: 'tipo', titulo: 'Tipo' },
              { chave: 'identificacaoInterna', titulo: 'Interno' }],
    campos: [{ nome: 'tipo', label: 'Tipo', opcoes: ['Graneleira', 'Baú', 'Bitrem', 'Frigorífica', 'Prancha'], obrigatorio: true },
             { nome: 'placa', label: 'Placa', obrigatorio: true }, { nome: 'identificacaoInterna', label: 'Identificação interna' }],
  },
  postos: {
    label: 'Postos', icon: 'fuel', rota: '/postos',
    colunas: [{ chave: 'nome', titulo: 'Nome' }, { chave: 'cidade', titulo: 'Cidade' }, { chave: 'estado', titulo: 'UF' }],
    campos: [{ nome: 'nome', label: 'Nome do posto', obrigatorio: true },
             { nome: 'cidade', label: 'Cidade', obrigatorio: true }, { nome: 'estado', label: 'UF', obrigatorio: true }],
  },
  oficinas: {
    label: 'Oficinas', icon: 'wrench', rota: '/oficinas',
    colunas: [{ chave: 'nome', titulo: 'Nome' }, { chave: 'cidade', titulo: 'Cidade' }, { chave: 'estado', titulo: 'UF' }],
    campos: [{ nome: 'nome', label: 'Nome da oficina', obrigatorio: true },
             { nome: 'cidade', label: 'Cidade', obrigatorio: true }, { nome: 'estado', label: 'UF', obrigatorio: true }],
  },
  empresas: {
    label: 'Empresas parceiras', icon: 'building', rota: '/empresas',
    colunas: [{ chave: 'nome', titulo: 'Nome' }, { chave: 'segmento', titulo: 'Segmento' },
              { chave: 'cidade', titulo: 'Cidade' }, { chave: 'estado', titulo: 'UF' }],
    campos: [{ nome: 'nome', label: 'Razão social', obrigatorio: true }, { nome: 'segmento', label: 'Segmento' },
             { nome: 'cidade', label: 'Cidade', obrigatorio: true }, { nome: 'estado', label: 'UF', obrigatorio: true },
             { nome: 'cnpjFicticio', label: 'CNPJ fictício' }],
  },
  avisos: {
    label: 'Avisos e notícias', icon: 'megaphone', rota: '/avisos',
    colunas: [{ chave: 'titulo', titulo: 'Título' }, { chave: 'tipo', titulo: 'Tipo' }],
    campos: [{ nome: 'titulo', label: 'Título', obrigatorio: true },
             { nome: 'mensagem', label: 'Mensagem', tipo: 'textarea', obrigatorio: true },
             { nome: 'tipo', label: 'Tipo', opcoes: ['INFORMATIVO', 'ALERTA', 'EVENTO'], obrigatorio: true }],
  },
};

const SECOES_GENERICAS = Object.keys(CONFIG) as SecaoGenerica[];
const NAV: { chave: Secao; label: string; icon: 'users' | (typeof CONFIG)[SecaoGenerica]['icon'] }[] = [
  { chave: 'motoristas', label: 'Motoristas', icon: 'users' },
  ...SECOES_GENERICAS.map((s) => ({ chave: s, label: CONFIG[s].label, icon: CONFIG[s].icon })),
];

export default function Admin() {
  const [secao, setSecao] = useState<Secao>('motoristas');

  return (
    <div className="admin">
      <header className="admin__head">
        <h1>Administração</h1>
        <p>Cadastros da transportadora</p>
      </header>

      <div className="admin__layout">
        <nav className="admin__menu">
          {NAV.map((item) => (
            <button key={item.chave} className={'admin__menu-item' + (secao === item.chave ? ' is-active' : '')}
              onClick={() => setSecao(item.chave)}>
              <Icon name={item.icon} size={16} />
              {item.label}
            </button>
          ))}
        </nav>

        {secao === 'motoristas' ? <Motoristas />
         : secao === 'caminhoes' ? <Caminhoes />
         : <SecaoGenericaView cfg={CONFIG[secao]} />}
      </div>
    </div>
  );
}

function SecaoGenericaView({ cfg }: { cfg: (typeof CONFIG)[SecaoGenerica] }) {
  const [modal, setModal] = useState(false);
  const { dados, carregando, erro, recarregar } = useApi<Record<string, unknown>[]>(cfg.rota);

  async function remover(id: string) {
    if (!confirm('Remover este registro? A ação não pode ser desfeita.')) return;
    try {
      await api.delete(`${cfg.rota}/${id}`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível remover.');
    }
  }

  return (
    <section className="admin__conteudo">
      <div className="admin__conteudo-head">
        <h2>{cfg.label}</h2>
        <button className="btn" onClick={() => setModal(true)}>
          <Icon name="plus" size={15} /> Adicionar
        </button>
      </div>

      {carregando ? <Carregando /> :
       erro ? <Erro mensagem={erro} aoTentarNovamente={recarregar} /> :
       !dados?.length ? <Vazio titulo={`Nenhum registro em ${cfg.label.toLowerCase()}`} /> : (
        <table className="admin__tabela">
          <thead>
            <tr>{cfg.colunas.map((c) => <th key={c.chave}>{c.titulo}</th>)}<th /></tr>
          </thead>
          <tbody>
            {dados.map((linha) => (
              <tr key={String(linha.id)}>
                {cfg.colunas.map((c) => <td key={c.chave}>{String(linha[c.chave] ?? '—')}</td>)}
                <td className="admin__acoes-col">
                  <button className="is-danger" title="Remover" onClick={() => remover(String(linha.id))}>
                    <Icon name="trash" size={15} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {modal && (
        <ModalCadastro
          cfg={cfg}
          onFechar={() => setModal(false)}
          onSalvo={() => { setModal(false); recarregar(); }}
        />
      )}
    </section>
  );
}

const PAPEL_LABEL: Record<Usuario['papel'], string> = { MOTORISTA: 'Motorista', GESTOR: 'Gestor' };
const STATUS_LABEL: Record<Usuario['statusAcesso'], string> = {
  PENDENTE: 'Pendente', APROVADO: 'Aprovado', BLOQUEADO: 'Bloqueado',
};

function Motoristas() {
  const [modal, setModal] = useState<'novo' | Usuario | null>(null);
  const { dados, carregando, erro, recarregar } = useApi<Usuario[]>('/usuarios');

  async function remover(id: string) {
    if (!confirm('Remover este usuário? A ação não pode ser desfeita.')) return;
    try {
      await api.delete(`/usuarios/${id}`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível remover.');
    }
  }

  async function mudarStatus(id: string, acao: 'aprovar' | 'bloquear') {
    try {
      await api.post(`/usuarios/${id}/${acao}`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível atualizar o status.');
    }
  }

  return (
    <section className="admin__conteudo">
      <div className="admin__conteudo-head">
        <h2>Motoristas</h2>
        <button className="btn" onClick={() => setModal('novo')}>
          <Icon name="plus" size={15} /> Adicionar
        </button>
      </div>

      {carregando ? <Carregando /> :
       erro ? <Erro mensagem={erro} aoTentarNovamente={recarregar} /> :
       !dados?.length ? <Vazio titulo="Nenhum motorista cadastrado" /> : (
        <table className="admin__tabela">
          <thead>
            <tr><th>Nome</th><th>E-mail</th><th>Papel</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {dados.map((u) => (
              <tr key={u.id}>
                <td>{u.nome}</td>
                <td>{u.email}</td>
                <td>{PAPEL_LABEL[u.papel]}</td>
                <td>
                  <span className={`admin__tag admin__tag--${u.statusAcesso.toLowerCase()}`}>
                    {STATUS_LABEL[u.statusAcesso]}
                  </span>
                </td>
                <td className="admin__acoes-col">
                  {u.statusAcesso === 'PENDENTE' && (
                    <button title="Aprovar" onClick={() => mudarStatus(u.id, 'aprovar')}>
                      <Icon name="check" size={15} />
                    </button>
                  )}
                  {u.statusAcesso !== 'BLOQUEADO' ? (
                    <button title="Bloquear" onClick={() => mudarStatus(u.id, 'bloquear')}>
                      <Icon name="shield" size={15} />
                    </button>
                  ) : (
                    <button title="Reativar" onClick={() => mudarStatus(u.id, 'aprovar')}>
                      <Icon name="shield" size={15} />
                    </button>
                  )}
                  <button title="Editar" onClick={() => setModal(u)}>
                    <Icon name="edit" size={15} />
                  </button>
                  <button className="is-danger" title="Remover" onClick={() => remover(u.id)}>
                    <Icon name="trash" size={15} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {modal && (
        <ModalMotorista
          usuario={modal === 'novo' ? undefined : modal}
          onFechar={() => setModal(null)}
          onSalvo={() => { setModal(null); recarregar(); }}
        />
      )}
    </section>
  );
}

function ModalMotorista({ usuario, onFechar, onSalvo }: {
  usuario?: Usuario; onFechar: () => void; onSalvo: () => void;
}) {
  const editando = !!usuario;
  const [nome, setNome] = useState(usuario?.nome ?? '');
  const [email, setEmail] = useState(usuario?.email ?? '');
  const [papel, setPapel] = useState<Usuario['papel']>(usuario?.papel ?? 'MOTORISTA');
  const [senha, setSenha] = useState('');
  // Vazio = usa o valor por km padrão da empresa.
  const [comissao, setComissao] = useState(usuario?.valorKmComissao != null ? String(usuario.valorKmComissao) : '');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      if (editando) {
        await api.put(`/usuarios/${usuario.id}`, { nome, email, papel, novaSenha: senha || undefined });
        await api.post(`/financeiro/valor-km/${usuario.id}`, { valorKm: comissao === '' ? null : comissao });
      } else {
        await api.post('/usuarios', { nome, email, papel, senha });
      }
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{editando ? 'Editar motorista' : 'Novo motorista'}</h3>

        <label className="campo">
          <span>Nome</span>
          <input value={nome} onChange={(e) => setNome(e.target.value)} />
        </label>
        <label className="campo">
          <span>E-mail</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="campo">
          <span>Papel</span>
          <select value={papel} onChange={(e) => setPapel(e.target.value as Usuario['papel'])}>
            <option value="MOTORISTA">Motorista</option>
            <option value="GESTOR">Gestor</option>
          </select>
        </label>
        <label className="campo">
          <span>{editando ? 'Nova senha (deixe em branco para manter)' : 'Senha'}</span>
          <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} />
        </label>

        <label className="campo">
          <span>Comissão em R$ por km (vazio = padrão da empresa)</span>
          <input value={comissao} inputMode="decimal" placeholder="0,35"
                 onChange={(e) => setComissao(e.target.value)} />
        </label>

        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={salvar} disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  );
}

function ModalCadastro({ cfg, onFechar, onSalvo }: {
  cfg: (typeof CONFIG)[SecaoGenerica]; onFechar: () => void; onSalvo: () => void;
}) {
  const [valores, setValores] = useState<Record<string, string | boolean>>({});
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    const faltando = cfg.campos.filter((c) => c.obrigatorio && !valores[c.nome]);
    if (faltando.length > 0) {
      setErro(`Preencha os campos obrigatórios: ${faltando.map((c) => c.label).join(', ')}.`);
      return;
    }
    setSalvando(true);
    setErro(null);
    try {
      await api.post(cfg.rota, valores);
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Novo — {cfg.label.toLowerCase()}</h3>
        {cfg.campos.map((c) => (
          <label className="campo" key={c.nome}>
            <span>{c.label}</span>
            {c.opcoes ? (
              <select onChange={(e) => setValores((v) => ({ ...v, [c.nome]: e.target.value }))} defaultValue="">
                <option value="" disabled>Selecione</option>
                {c.opcoes.map((o) => <option key={o} value={o}>{o}</option>)}
              </select>
            ) : c.tipo === 'textarea' ? (
              <textarea rows={3} onChange={(e) => setValores((v) => ({ ...v, [c.nome]: e.target.value }))} />
            ) : (
              <input onChange={(e) => setValores((v) => ({ ...v, [c.nome]: e.target.value }))} />
            )}
          </label>
        ))}
        {cfg.label.startsWith('Avisos') && (
          <label className="campo campo--check">
            <input type="checkbox" onChange={(e) => setValores((v) => ({ ...v, fixado: e.target.checked }))} />
            <span>Fixar no topo do painel dos motoristas</span>
          </label>
        )}
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={salvar} disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ caminhões

/**
 * Seção própria (e não a tabela genérica) porque o caminhão tem dono, e dono é
 * um objeto — a tabela genérica imprimiria "[object Object]". Além disso, é
 * aqui que se decide quem pode rodar com o quê.
 */
function Caminhoes() {
  const { dados, carregando, erro, recarregar } = useApi<Caminhao[]>('/caminhoes');
  const [modal, setModal] = useState(false);
  const [definindoDono, setDefinindoDono] = useState<Caminhao | null>(null);

  async function remover(id: string) {
    if (!confirm('Remover este caminhão? A ação não pode ser desfeita.')) return;
    try {
      await api.delete(`/caminhoes/${id}`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível remover.');
    }
  }

  return (
    <section className="admin__conteudo">
      <div className="admin__conteudo-head">
        <h2>Caminhões</h2>
        <button className="btn" onClick={() => setModal(true)}>
          <Icon name="plus" size={15} /> Adicionar
        </button>
      </div>

      {carregando ? <Carregando /> :
       erro ? <Erro mensagem={erro} aoTentarNovamente={recarregar} /> :
       !dados?.length ? <Vazio titulo="Nenhum caminhão cadastrado" /> : (
        <>
          <p className="admin__nota">
            Caminhão <b>sem dono</b> é da empresa e qualquer motorista usa.
            Com dono, só ele dirige — é assim que se limita quem roda com o quê.
          </p>
          <table className="admin__tabela">
            <thead>
              <tr><th>Placa</th><th>Veículo</th><th>Interno</th><th>Dono</th><th /></tr>
            </thead>
            <tbody>
              {dados.map((c) => (
                <tr key={c.id}>
                  <td>{c.placa}</td>
                  <td>{c.marca} {c.modelo}</td>
                  <td>{c.identificacaoInterna || '—'}</td>
                  <td>
                    {c.dono
                      ? <span className="admin__tag admin__tag--aprovado">{c.dono.nome}</span>
                      : <span className="admin__tag">Da empresa</span>}
                  </td>
                  <td className="admin__acoes-col">
                    <button title="Definir dono" onClick={() => setDefinindoDono(c)}>
                      <Icon name="users" size={15} />
                    </button>
                    <button className="is-danger" title="Remover" onClick={() => remover(c.id)}>
                      <Icon name="trash" size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {modal && (
        <ModalCadastro cfg={CONFIG.caminhoes} onFechar={() => setModal(false)}
                       onSalvo={() => { setModal(false); recarregar(); }} />
      )}
      {definindoDono && (
        <ModalDono caminhao={definindoDono} onFechar={() => setDefinindoDono(null)}
                   onSalvo={() => { setDefinindoDono(null); recarregar(); }} />
      )}
    </section>
  );
}

function ModalDono({ caminhao, onFechar, onSalvo }: {
  caminhao: Caminhao; onFechar: () => void; onSalvo: () => void;
}) {
  const { dados: usuarios } = useApi<Usuario[]>('/usuarios');
  const [motoristaId, setMotoristaId] = useState(caminhao.dono?.id ?? '');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      await api.post(`/caminhoes/${caminhao.id}/dono`, { motoristaId: motoristaId || null });
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Dono do {caminhao.marca} {caminhao.modelo}</h3>
        <p className="admin__nota-modal">Placa {caminhao.placa}</p>
        <label className="campo">
          <span>De quem é este caminhão?</span>
          <select value={motoristaId} onChange={(e) => setMotoristaId(e.target.value)}>
            <option value="">Da empresa (qualquer motorista usa)</option>
            {usuarios?.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
          </select>
        </label>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={salvar} disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  );
}
