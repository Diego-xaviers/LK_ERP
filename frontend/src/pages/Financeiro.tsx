import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useSessao } from '../auth';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import './Financeiro.css';

interface ViagemPagavel {
  id: string; numero: number; origem: string; destino: string; carga: string;
  finalizadaEm: string; valorFrete: number; despesas: number; base: number;
  comissao: number; conferencia: string;
}
interface ResumoMotorista {
  motoristaId: string; motorista: string; percentual: number;
  percentualProprio?: number; viagens: ViagemPagavel[]; comissaoTotal: number;
}
interface Movimento {
  id: string; tipo: 'FRETE' | 'COMISSAO' | 'AJUSTE'; entrada: boolean;
  valor: number; descricao: string; saldoDepois: number; criadoEm: string;
}
interface Painel {
  saldo: number; percentualPadrao: number; aPagar: number;
  motoristas: ResumoMotorista[]; extrato: Movimento[];
}
interface Pagamento {
  id: string; numero: number; valor: number; percentualAplicado: number;
  baseFrete: number; baseDespesas: number; criadoEm: string; criadoPor?: string; observacao?: string;
}
interface Ganhos extends ResumoMotorista {
  pagamentos: Pagamento[];
  jaRecebido: number;
}

export default function Financeiro() {
  const { eGestor } = useSessao();
  return eGestor ? <VisaoGestor /> : <VisaoMotorista />;
}

// ---------------------------------------------------------------- gestor

function VisaoGestor() {
  const { dados, carregando, erro, recarregar } = useApi<Painel>('/financeiro/painel');
  const [acertando, setAcertando] = useState<ResumoMotorista | null>(null);
  const [ajuste, setAjuste] = useState(false);

  if (carregando) return <Carregando texto="Carregando o financeiro..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!dados) return null;

  return (
    <div className="fin">
      <header className="fin__head">
        <div>
          <h1>Financeiro</h1>
          <p>Caixa da transportadora e acerto com os motoristas</p>
        </div>
        <button className="btn btn--ghost" onClick={() => setAjuste(true)}>
          <Icon name="wallet" size={15} /> Lançar aporte ou retirada
        </button>
      </header>

      <section className="fin__cartoes">
        <div className="fin__cartao fin__cartao--saldo">
          <span>Saldo em caixa</span>
          <strong>{brl(dados.saldo)}</strong>
        </div>
        <div className="fin__cartao">
          <span>A pagar aos motoristas</span>
          <strong className={dados.aPagar > 0 ? 'is-devendo' : ''}>{brl(dados.aPagar)}</strong>
        </div>
        <div className="fin__cartao">
          <span>Comissão padrão</span>
          <strong>{dados.percentualPadrao}%</strong>
        </div>
      </section>

      <section className="fin__bloco">
        <h2>Acertos pendentes</h2>
        {dados.motoristas.length === 0 ? (
          <Vazio titulo="Nenhum acerto pendente"
                 descricao="As viagens liberadas aparecem aqui para pagamento." />
        ) : (
          <div className="fin__motoristas">
            {dados.motoristas.map((m) => (
              <article className="fin__motorista" key={m.motoristaId}>
                <header>
                  <div>
                    <h3>{m.motorista}</h3>
                    <span>{m.viagens.length} viagem(ns) · comissão de {m.percentual}%
                      {m.percentualProprio != null && ' (própria)'}</span>
                  </div>
                  <strong>{brl(m.comissaoTotal)}</strong>
                </header>

                <table className="fin__tabela">
                  <thead>
                    <tr><th>Viagem</th><th>Rota</th><th>Frete</th><th>Despesas</th><th>Base</th><th>Comissão</th></tr>
                  </thead>
                  <tbody>
                    {m.viagens.map((v) => (
                      <tr key={v.id}>
                        <td>#{v.numero}{v.conferencia === 'LIBERADA' && <em title="Liberada pelo gestor"> ✓</em>}</td>
                        <td>{v.origem} → {v.destino}</td>
                        <td>{brl(v.valorFrete)}</td>
                        <td className={v.despesas > 0 ? "is-saida" : ""}>{v.despesas > 0 ? "−" : ""}{brl(v.despesas)}</td>
                        <td>{brl(v.base)}</td>
                        <td className="is-forte">{brl(v.comissao)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <footer>
                  <button className="btn" onClick={() => setAcertando(m)}
                          disabled={m.comissaoTotal > dados.saldo}>
                    Pagar {brl(m.comissaoTotal)}
                  </button>
                  {m.comissaoTotal > dados.saldo && (
                    <span className="fin__sem-saldo">Saldo em caixa não cobre este acerto.</span>
                  )}
                </footer>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="fin__bloco">
        <h2>Extrato do caixa</h2>
        {dados.extrato.length === 0 ? (
          <Vazio titulo="Sem movimentação ainda" />
        ) : (
          <table className="fin__tabela fin__extrato">
            <thead>
              <tr><th>Quando</th><th>Movimento</th><th>Valor</th><th>Saldo</th></tr>
            </thead>
            <tbody>
              {dados.extrato.map((m) => (
                <tr key={m.id}>
                  <td>{quando(m.criadoEm)}</td>
                  <td><span className={'fin__tag is-' + m.tipo.toLowerCase()}>{rotulo(m.tipo)}</span> {m.descricao}</td>
                  <td className={m.entrada ? 'is-entrada' : 'is-saida'}>
                    {m.entrada ? '+' : '−'}{brl(m.valor)}
                  </td>
                  <td>{brl(m.saldoDepois)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {acertando && (
        <ModalAcerto resumo={acertando} onFechar={() => setAcertando(null)}
                     onPago={() => { setAcertando(null); recarregar(); }} />
      )}
      {ajuste && (
        <ModalAjuste onFechar={() => setAjuste(false)}
                     onFeito={() => { setAjuste(false); recarregar(); }} />
      )}
    </div>
  );
}

function ModalAcerto({ resumo, onFechar, onPago }: {
  resumo: ResumoMotorista; onFechar: () => void; onPago: () => void;
}) {
  const [observacao, setObservacao] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const pagar = () => api.post(`/financeiro/pagar/${resumo.motoristaId}`, { observacao });

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Acerto com {resumo.motorista}</h3>
        <div className="fin__resumo-modal">
          <div><span>Viagens</span><strong>{resumo.viagens.length}</strong></div>
          <div><span>Comissão</span><strong>{resumo.percentual}%</strong></div>
          <div><span>Total</span><strong className="is-forte">{brl(resumo.comissaoTotal)}</strong></div>
        </div>
        <label className="campo">
          <span>Observação (opcional)</span>
          <textarea rows={2} value={observacao} onChange={(e) => setObservacao(e.target.value)} />
        </label>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={() => { setErro(null); setSalvando(true); }} disabled={salvando}>
            Confirmar pagamento
          </button>
        </div>
      </div>

      {salvando && (
        <Processo
          etapas={['Somando as viagens do período', 'Calculando a comissão',
                   'Baixando as viagens pagas', 'Creditando a carteira do motorista']}
          sucesso="Acerto pago e creditado."
          trabalho={pagar}
          aoConcluir={onPago}
          aoFalhar={(m) => { setSalvando(false); setErro(m); }}
        />
      )}
    </div>
  );
}

function ModalAjuste({ onFechar, onFeito }: { onFechar: () => void; onFeito: () => void }) {
  const [valor, setValor] = useState('');
  const [descricao, setDescricao] = useState('');
  const [sentido, setSentido] = useState<'aporte' | 'retirada'>('aporte');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function lancar() {
    setSalvando(true);
    setErro(null);
    try {
      const n = Math.abs(parseFloat(valor.replace(',', '.') || '0'));
      await api.post('/financeiro/ajuste', { valor: sentido === 'aporte' ? n : -n, descricao });
      onFeito();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível lançar.');
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Aporte ou retirada</h3>
        <div className="fin__sentido">
          <button className={sentido === 'aporte' ? 'is-on' : ''} onClick={() => setSentido('aporte')}>
            Aporte (entra)
          </button>
          <button className={sentido === 'retirada' ? 'is-on' : ''} onClick={() => setSentido('retirada')}>
            Retirada (sai)
          </button>
        </div>
        <label className="campo">
          <span>Valor (R$)</span>
          <input value={valor} inputMode="decimal" autoFocus onChange={(e) => setValor(e.target.value)} />
        </label>
        <label className="campo">
          <span>Descrição</span>
          <input value={descricao} onChange={(e) => setDescricao(e.target.value)}
                 placeholder="Ex.: aporte do sócio, compra de caminhão" />
        </label>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={lancar} disabled={salvando || !valor}>
            {salvando ? 'Lançando...' : 'Lançar'}
          </button>
        </div>
      </div>
    </div>
  );
}

// -------------------------------------------------------------- motorista

function VisaoMotorista() {
  const { usuario } = useSessao();
  const { dados, carregando, erro, recarregar } = useApi<Ganhos>(`/financeiro/meus-ganhos/${usuario.id}`);

  if (carregando) return <Carregando texto="Carregando seus ganhos..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!dados) return null;

  return (
    <div className="fin">
      <header className="fin__head">
        <div>
          <h1>Meus ganhos</h1>
          <p>Sua comissão é de {dados.percentual}% sobre o frete menos as despesas da viagem</p>
        </div>
      </header>

      <section className="fin__cartoes">
        <div className="fin__cartao fin__cartao--saldo">
          <span>A receber</span>
          <strong>{brl(dados.comissaoTotal)}</strong>
        </div>
        <div className="fin__cartao">
          <span>Já recebido</span>
          <strong>{brl(dados.jaRecebido)}</strong>
        </div>
        <div className="fin__cartao">
          <span>Viagens aguardando acerto</span>
          <strong>{dados.viagens.length}</strong>
        </div>
      </section>

      <section className="fin__bloco">
        <h2>Aguardando acerto</h2>
        {dados.viagens.length === 0 ? (
          <Vazio titulo="Nada a receber no momento"
                 descricao="Viagens retidas na conferência não entram aqui até serem liberadas." />
        ) : (
          <table className="fin__tabela">
            <thead>
              <tr><th>Viagem</th><th>Rota</th><th>Frete</th><th>Despesas</th><th>Base</th><th>Sua comissão</th></tr>
            </thead>
            <tbody>
              {dados.viagens.map((v) => (
                <tr key={v.id}>
                  <td>#{v.numero}</td>
                  <td>{v.origem} → {v.destino}</td>
                  <td>{brl(v.valorFrete)}</td>
                  <td className={v.despesas > 0 ? "is-saida" : ""}>{v.despesas > 0 ? "−" : ""}{brl(v.despesas)}</td>
                  <td>{brl(v.base)}</td>
                  <td className="is-forte">{brl(v.comissao)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="fin__bloco">
        <h2>Acertos recebidos</h2>
        {dados.pagamentos.length === 0 ? (
          <Vazio titulo="Nenhum acerto ainda" />
        ) : (
          <table className="fin__tabela">
            <thead>
              <tr><th>Acerto</th><th>Quando</th><th>Base</th><th>%</th><th>Valor</th></tr>
            </thead>
            <tbody>
              {dados.pagamentos.map((p) => (
                <tr key={p.id}>
                  <td>#{p.numero}</td>
                  <td>{quando(p.criadoEm)}</td>
                  <td>{brl(p.baseFrete - p.baseDespesas)}</td>
                  <td>{p.percentualAplicado}%</td>
                  <td className="is-forte">{brl(p.valor)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

const brl = (v: number) =>
  (v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

function quando(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function rotulo(t: Movimento['tipo']) {
  return t === 'FRETE' ? 'Frete' : t === 'COMISSAO' ? 'Comissão' : 'Ajuste';
}
