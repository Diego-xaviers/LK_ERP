import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { Caminhao, Carreta, EmpresaParceira, Viagem } from '../api/tipos';
import { Carregando, Erro } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import { useUsuario } from '../auth';
import './NovaViagem.css';

export default function NovaViagem() {
  const navigate = useNavigate();
  const usuario = useUsuario();
  const caminhoes = useApi<Caminhao[]>('/caminhoes');
  const carretas = useApi<Carreta[]>('/carretas');
  const empresas = useApi<EmpresaParceira[]>('/empresas');

  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [criada, setCriada] = useState<Viagem | null>(null);
  const [documentosProntos, setDocumentosProntos] = useState(false);
  const [processo, setProcesso] = useState<null | 'documentos' | 'partida'>(null);

  const [form, setForm] = useState({
    origem: '', destino: '',
    empresaRemetente: '', empresaDestinataria: '',
    carga: '', pesoKg: '', valorCarga: '', valorFrete: '',
    caminhaoId: '', carretaId: '',
  });

  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    setSalvando(true);
    setErro(null);
    try {
      const viagem = await api.post<Viagem>('/viagens', {
        origem: form.origem,
        destino: form.destino,
        empresaRemetente: form.empresaRemetente,
        empresaDestinataria: form.empresaDestinataria,
        carga: form.carga,
        pesoKg: Number(form.pesoKg),
        valorCarga: form.valorCarga ? Number(form.valorCarga) : null,
        valorFrete: form.valorFrete ? Number(form.valorFrete) : null,
        motoristaId: usuario.id,
        caminhaoId: form.caminhaoId,
        carretaId: form.carretaId || null,
      });
      setCriada(viagem);
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível criar a viagem.');
    } finally {
      setSalvando(false);
    }
  }

  /**
   * Gerar a documentação e iniciar não são ações excludentes — a documentação é
   * o passo anterior à partida. Por isso gerar não navega para lugar nenhum: o
   * botão de iniciar continua na tela.
   *
   * As duas passam pelo overlay de etapas (emitindo NF, CT-e, MDF-e). A espera é
   * encenada, o resultado não: se a API recusar, o overlay mostra o erro.
   */
  const emissao = ['Emitindo Nota Fiscal', 'Emitindo CT-e', 'Emitindo MDF-e'];

  if (caminhoes.carregando || carretas.carregando || empresas.carregando) {
    return <Carregando texto="Carregando cadastros..." />;
  }
  if (caminhoes.erro) {
    return <Erro mensagem={caminhoes.erro} aoTentarNovamente={caminhoes.recarregar} />;
  }

  // ----- Confirmação -----
  if (criada) {
    return (
      <div className="nv">
        <div className="nv__sucesso">
          <span className="nv__sucesso-icone"><Icon name="check" size={24} strokeWidth={2} /></span>
          <h1>Viagem #{criada.numero} criada</h1>
          <p>{criada.origem} → {criada.destino} · {criada.carga}</p>
          {erro && <div className="nv__erro">{erro}</div>}

          {documentosProntos && (
            <p className="nv__docs-ok">
              <Icon name="check" size={15} /> NF, CT-e e MDF-e emitidos.{' '}
              <Link to={`/documentos?viagem=${criada.id}`}>Ver documentos</Link>
            </p>
          )}

          <div className="nv__sucesso-acoes">
            {!documentosProntos && (
              <button className="btn btn--ghost" onClick={() => setProcesso('documentos')}>
                Gerar documentação
              </button>
            )}
            <button className="btn" onClick={() => setProcesso('partida')}>
              Iniciar viagem
            </button>
          </div>
          <p className="nv__sucesso-dica">
            Iniciar já emite a documentação que faltar — e a viagem fica esperando em
            <Link to="/viagem"> Viagem atual</Link> se você sair daqui.
          </p>
        </div>

        {processo === 'documentos' && (
          <Processo
            etapas={emissao}
            sucesso="Documentos emitidos com sucesso."
            trabalho={() => api.post(`/viagens/${criada.id}/documentos`)}
            aoConcluir={() => { setProcesso(null); setDocumentosProntos(true); }}
            aoFalhar={(m) => { setProcesso(null); setErro(m); }}
          />
        )}

        {processo === 'partida' && (
          <Processo
            etapas={[...(documentosProntos ? [] : emissao), 'Liberando a saída do pátio']}
            sucesso="Tudo pronto. Boa viagem!"
            trabalho={async () => {
              // Idempotente no servidor: não duplica se já tiver sido gerado.
              await api.post(`/viagens/${criada.id}/documentos`);
              await api.post(`/viagens/${criada.id}/iniciar`);
            }}
            aoConcluir={() => navigate('/viagem')}
            aoFalhar={(m) => { setProcesso(null); setErro(m); }}
          />
        )}
      </div>
    );
  }

  // ----- Formulário -----
  return (
    <div className="nv">
      <header className="nv__head">
        <h1>Nova viagem</h1>
        <p>Pegue a carga no jogo e registre aqui — leva menos de um minuto</p>
      </header>

      <form className="nv__form" onSubmit={salvar}>
        <fieldset className="nv__bloco">
          <legend>Rota</legend>
          <div className="nv__linha">
            <label className="campo">
              <span>Origem</span>
              <input value={form.origem} onChange={(e) => set('origem', e.target.value)}
                placeholder="Cuiabá/MT" required />
            </label>
            <label className="campo">
              <span>Destino</span>
              <input value={form.destino} onChange={(e) => set('destino', e.target.value)}
                placeholder="Sinop/MT" required />
            </label>
          </div>
          <div className="nv__linha">
            <label className="campo">
              <span>Empresa remetente</span>
              <input list="empresas" value={form.empresaRemetente}
                onChange={(e) => set('empresaRemetente', e.target.value)} required />
            </label>
            <label className="campo">
              <span>Empresa destinatária</span>
              <input list="empresas" value={form.empresaDestinataria}
                onChange={(e) => set('empresaDestinataria', e.target.value)} required />
            </label>
          </div>
          <datalist id="empresas">
            {empresas.dados?.map((e) => <option key={e.id} value={e.nome} />)}
          </datalist>
        </fieldset>

        <fieldset className="nv__bloco">
          <legend>Carga</legend>
          <div className="nv__linha">
            <label className="campo">
              <span>Descrição</span>
              <input value={form.carga} onChange={(e) => set('carga', e.target.value)}
                placeholder="Soja" required />
            </label>
            <label className="campo campo--curto">
              <span>Peso (kg)</span>
              <input type="number" step="0.001" value={form.pesoKg}
                onChange={(e) => set('pesoKg', e.target.value)} placeholder="28000" required />
            </label>
          </div>
          <div className="nv__linha">
            <label className="campo">
              <span>Valor da carga <em>(opcional)</em></span>
              <input type="number" step="0.01" value={form.valorCarga}
                onChange={(e) => set('valorCarga', e.target.value)} />
            </label>
            <label className="campo">
              <span>Valor do frete <em>(opcional)</em></span>
              <small className="nova__aviso-frete">
                Preenchido, a viagem fica retida na conferência: valor digitado não
                veio de uma demanda. Em Logística o frete já vem definido.
              </small>
              <input type="number" step="0.01" value={form.valorFrete}
                onChange={(e) => set('valorFrete', e.target.value)} />
            </label>
          </div>
        </fieldset>

        <fieldset className="nv__bloco">
          <legend>Equipamento</legend>
          <div className="nv__linha">
            <label className="campo">
              <span>Caminhão</span>
              <select value={form.caminhaoId} onChange={(e) => set('caminhaoId', e.target.value)} required>
                <option value="">Selecione</option>
                {/* Só o que é dele ou da empresa: oferecer os outros levaria a um erro no envio. */}
                {caminhoes.dados?.filter((c) => !c.dono || c.dono.id === usuario.id).map((c) => (
                  <option key={c.id} value={c.id}>{c.marca} {c.modelo} — {c.placa}</option>
                ))}
              </select>
            </label>
            <label className="campo">
              <span>Carreta <em>(opcional)</em></span>
              <select value={form.carretaId} onChange={(e) => set('carretaId', e.target.value)}>
                <option value="">Sem carreta</option>
                {carretas.dados?.map((c) => (
                  <option key={c.id} value={c.id}>{c.tipo} — {c.placa}</option>
                ))}
              </select>
            </label>
          </div>
        </fieldset>

        {erro && <div className="nv__erro">{erro}</div>}

        <div className="nv__rodape">
          <span className="nv__nota">Motorista e data/hora são preenchidos automaticamente.</span>
          <button className="btn" type="submit" disabled={salvando}>
            {salvando ? 'Criando...' : 'Criar viagem'}
          </button>
        </div>
      </form>
    </div>
  );
}
