import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useSessao } from '../auth';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import CnhDocument, { CnhDados } from '../components/documents/CnhDocument';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import { Usuario } from '../api/tipos';
import './Habilitacao.css';

export default function Habilitacao() {
  const { usuario, eGestor } = useSessao();
  const [verDe, setVerDe] = useState<string>(usuario.id);

  return (
    <div className="hab">
      <header className="hab__head">
        <div>
          <h1>Habilitação</h1>
          <p>
            {eGestor
              ? 'CNH dos motoristas — emissão, renovação e pontuação'
              : 'Sua CNH e a pontuação que sobrou nela'}
          </p>
        </div>
        {eGestor && <SeletorDeMotorista atual={verDe} aoTrocar={setVerDe} />}
      </header>

      <Carteira motoristaId={verDe} podeGerir={eGestor} />
    </div>
  );
}

function SeletorDeMotorista({ atual, aoTrocar }: { atual: string; aoTrocar: (id: string) => void }) {
  const { dados } = useApi<Usuario[]>('/usuarios');
  return (
    <label className="hab__seletor">
      <span>Ver a carteira de</span>
      <select value={atual} onChange={(e) => aoTrocar(e.target.value)}>
        {dados?.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
      </select>
    </label>
  );
}

function Carteira({ motoristaId, podeGerir }: { motoristaId: string; podeGerir: boolean }) {
  const { dados, carregando, erro, recarregar } = useApi<CnhDados>(`/cnh/${motoristaId}`);
  const [acao, setAcao] = useState<'emitir' | 'reabilitar' | 'suspender' | null>(null);

  if (carregando) return <Carregando texto="Carregando a carteira..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;

  // 204: motorista ainda não tem CNH emitida.
  if (!dados) {
    return (
      <>
        <Vazio titulo="Sem CNH emitida"
               descricao={podeGerir
                 ? 'Emita a habilitação para este motorista poder pegar cargas.'
                 : 'Você ainda não pode pegar carga. Peça a emissão à gestão.'}
               acao={podeGerir
                 ? <button className="btn" onClick={() => setAcao('emitir')}>Emitir CNH</button>
                 : undefined} />
        {acao && <ModalAcao acao={acao} motoristaId={motoristaId}
                            onFechar={() => setAcao(null)}
                            onFeito={() => { setAcao(null); recarregar(); }} />}
      </>
    );
  }

  return (
    <div className="hab__conteudo">
      <CnhDocument dados={dados} />

      <aside className="hab__lateral">
        <div className="hab__cartao">
          <h2>Situação</h2>
          <dl>
            <dt>Estado</dt>
            <dd><span className={'hab__tag is-' + dados.estado.toLowerCase()}>{dados.estado}</span></dd>
            <dt>Pontos restantes</dt>
            <dd>{dados.pontos} de {dados.pontosIniciais}</dd>
            <dt>Validade</dt>
            <dd>{new Date(dados.validade + 'T00:00:00').toLocaleDateString('pt-BR')}</dd>
            {dados.emitidaPor && <><dt>Emitida por</dt><dd>{dados.emitidaPor}</dd></>}
          </dl>
          {dados.observacoes && <p className="hab__obs">{dados.observacoes}</p>}
        </div>

        <div className="hab__cartao">
          <h2>Como se perdem pontos</h2>
          <ul className="hab__regras">
            <li><b>−5</b> a cada multa registrada na viagem</li>
            <li><b>−3</b> a cada avaria detectada pela telemetria</li>
          </ul>
          <p className="hab__nota">
            Zerando os pontos ou vencendo o prazo, a carteira é suspensa e o
            motorista não consegue pegar carga até a gestão regularizar.
          </p>
        </div>

        {podeGerir && (
          <div className="hab__cartao">
            <h2>Gestão</h2>
            <div className="hab__acoes">
              <button className="btn" onClick={() => setAcao('emitir')}>
                <Icon name="check" size={15} /> Renovar (prazo e pontos cheios)
              </button>
              <button className="btn btn--ghost" onClick={() => setAcao('reabilitar')}>
                Devolver pontos
              </button>
              <button className="btn btn--ghost" onClick={() => setAcao('suspender')}>
                Suspender
              </button>
            </div>
          </div>
        )}

        <button className="btn btn--ghost hab__imprimir" onClick={() => window.print()}>
          Imprimir / PDF
        </button>
      </aside>

      {acao && <ModalAcao acao={acao} motoristaId={motoristaId}
                          onFechar={() => setAcao(null)}
                          onFeito={() => { setAcao(null); recarregar(); }} />}
    </div>
  );
}

function ModalAcao({ acao, motoristaId, onFechar, onFeito }: {
  acao: 'emitir' | 'reabilitar' | 'suspender';
  motoristaId: string; onFechar: () => void; onFeito: () => void;
}) {
  const [categoria, setCategoria] = useState('E');
  const [validade, setValidade] = useState('');
  const [observacao, setObservacao] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const titulo = acao === 'emitir' ? 'Emitir / renovar CNH'
               : acao === 'reabilitar' ? 'Devolver pontos' : 'Suspender CNH';

  const confirmar = () => acao === 'emitir'
    ? api.post(`/cnh/${motoristaId}/emitir`, { categoria, validade: validade || undefined })
    : api.post(`/cnh/${motoristaId}/${acao}`, { observacao });

  // Só a emissão ganha cerimônia: é a que produz um documento.
  const etapas = acao === 'emitir'
    ? ['Conferindo os dados do condutor', 'Gerando o número de registro', 'Imprimindo a via']
    : acao === 'reabilitar'
      ? ['Recalculando a pontuação', 'Reabilitando a carteira']
      : ['Registrando a decisão', 'Suspendendo a carteira'];

  const sucesso = acao === 'emitir' ? 'CNH emitida.'
                : acao === 'reabilitar' ? 'Pontos devolvidos.' : 'CNH suspensa.';

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{titulo}</h3>

        {acao === 'emitir' ? (
          <>
            <p className="hab__nota-modal">
              A emissão zera o histórico de pontos e dá um novo prazo.
            </p>
            <label className="campo">
              <span>Categoria</span>
              <select value={categoria} onChange={(e) => setCategoria(e.target.value)}>
                {['B', 'C', 'D', 'E'].map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
            <label className="campo">
              <span>Validade (vazio = 3 meses a partir de hoje)</span>
              <input type="date" value={validade} onChange={(e) => setValidade(e.target.value)} />
            </label>
          </>
        ) : (
          <label className="campo">
            <span>Observação</span>
            <textarea rows={3} value={observacao} autoFocus
                      onChange={(e) => setObservacao(e.target.value)} />
          </label>
        )}

        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={() => { setErro(null); setSalvando(true); }} disabled={salvando}>
            Confirmar
          </button>
        </div>
      </div>

      {salvando && (
        <Processo
          etapas={etapas}
          sucesso={sucesso}
          trabalho={confirmar}
          aoConcluir={onFeito}
          aoFalhar={(m) => { setSalvando(false); setErro(m); }}
        />
      )}
    </div>
  );
}
