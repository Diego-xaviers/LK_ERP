import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { useApi } from '../hooks/useApi';
import { useSessao } from '../auth';
import { Carregando, Erro, Vazio } from '../components/ui/Estado';
import Icon from '../components/ui/Icon';
import Processo from '../components/ui/Processo';
import { reduzirImagem } from '../utils/imagem';
import './Loja.css';

interface Item {
  id: string; nome: string; descricao?: string; categoria?: string;
  preco: number; estoque?: number | null; ativo: boolean; disponivel: boolean;
  imagemBase64?: string;
}
interface Vitrine { meusCreditos: number; gestor: boolean; itens: Item[] }
interface Compra {
  id: string; motorista: string; nomeItem: string; quantidade: number;
  valorUnitario: number; valorTotal: number; criadoEm: string;
}

export default function Loja() {
  const { usuario, eGestor } = useSessao();
  const { dados, carregando, erro, recarregar } = useApi<Vitrine>('/loja');
  const { dados: compras, recarregar: recarregarCompras } =
    useApi<Compra[]>(`/loja/compras/${usuario.id}`);
  const [editando, setEditando] = useState<'novo' | Item | null>(null);
  const [comprando, setComprando] = useState<Item | null>(null);

  if (carregando) return <Carregando texto="Abrindo a loja..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!dados) return null;

  async function remover(i: Item) {
    if (!confirm(`Remover "${i.nome}" da loja?`)) return;
    try {
      await api.delete(`/loja/itens/${i.id}`);
      recarregar();
    } catch (e) {
      alert(e instanceof ApiError ? e.message : 'Não foi possível remover.');
    }
  }

  const porCategoria = new Map<string, Item[]>();
  dados.itens.forEach((i) => {
    const c = i.categoria?.trim() || 'Geral';
    porCategoria.set(c, [...(porCategoria.get(c) ?? []), i]);
  });

  return (
    <div className="loja">
      <header className="loja__head">
        <div>
          <h1>Loja</h1>
          <p>Gaste seus créditos — o catálogo é definido pela gestão</p>
        </div>
        <div className="loja__topo-acoes">
          <div className="loja__creditos">
            <span>Seus créditos</span>
            <strong>{brl(dados.meusCreditos)}</strong>
          </div>
          {eGestor && (
            <button className="btn" onClick={() => setEditando('novo')}>
              <Icon name="plus" size={15} /> Novo item
            </button>
          )}
        </div>
      </header>

      {dados.itens.length === 0 ? (
        <Vazio titulo="A loja está vazia"
               descricao={eGestor ? 'Cadastre o primeiro item.' : 'A gestão ainda não colocou nada à venda.'} />
      ) : (
        [...porCategoria.entries()].map(([categoria, itens]) => (
          <section className="loja__secao" key={categoria}>
            <h2>{categoria}</h2>
            <div className="loja__grade">
              {itens.map((i) => (
                <article className={"loja__item" + (i.disponivel ? "" : " is-indisponivel")} key={i.id}>
                  {i.imagemBase64 && (
                    <div className="loja__foto"><img src={i.imagemBase64} alt={i.nome} /></div>
                  )}
                  <header>
                    <h3>{i.nome}</h3>
                    <strong>{brl(i.preco)}</strong>
                  </header>
                  {i.descricao && <p>{i.descricao}</p>}
                  <div className="loja__estoque">
                    {i.estoque == null
                      ? <span>Estoque ilimitado</span>
                      : <span className={i.estoque === 0 ? 'is-zerado' : ''}>
                          {i.estoque === 0 ? 'Esgotado' : `${i.estoque} em estoque`}
                        </span>}
                    {!i.ativo && <span className="is-zerado">Fora de venda</span>}
                  </div>
                  <footer>
                    <button className="btn" disabled={!i.disponivel || i.preco > dados.meusCreditos}
                            onClick={() => setComprando(i)}>
                      Comprar
                    </button>
                    {eGestor && (
                      <>
                        <button className="btn btn--ghost" onClick={() => setEditando(i)}>Editar</button>
                        <button className="btn btn--ghost" onClick={() => remover(i)}>Remover</button>
                      </>
                    )}
                  </footer>
                  {i.disponivel && i.preco > dados.meusCreditos && (
                    <small className="loja__sem-credito">Créditos insuficientes</small>
                  )}
                </article>
              ))}
            </div>
          </section>
        ))
      )}

      <section className="loja__secao">
        <h2>Minhas compras</h2>
        {!compras?.length ? (
          <Vazio titulo="Você ainda não comprou nada" />
        ) : (
          <table className="loja__tabela">
            <thead><tr><th>Item</th><th>Qtd</th><th>Unitário</th><th>Total</th><th>Quando</th></tr></thead>
            <tbody>
              {compras.map((c) => (
                <tr key={c.id}>
                  <td>{c.nomeItem}</td>
                  <td>{c.quantidade}</td>
                  <td>{brl(c.valorUnitario)}</td>
                  <td className="is-forte">{brl(c.valorTotal)}</td>
                  <td>{new Date(c.criadoEm).toLocaleString('pt-BR', {
                    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {editando && (
        <ModalItem item={editando === 'novo' ? undefined : editando}
                   onFechar={() => setEditando(null)}
                   onSalvo={() => { setEditando(null); recarregar(); }} />
      )}
      {comprando && (
        <ModalCompra item={comprando} creditos={dados.meusCreditos}
                     onFechar={() => setComprando(null)}
                     onComprado={() => { setComprando(null); recarregar(); recarregarCompras(); }} />
      )}
    </div>
  );
}

function ModalCompra({ item, creditos, onFechar, onComprado }: {
  item: Item; creditos: number; onFechar: () => void; onComprado: () => void;
}) {
  const [quantidade, setQuantidade] = useState(1);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const total = item.preco * quantidade;
  const maximo = item.estoque == null ? 99 : item.estoque;

  const comprar = () => api.post(`/loja/comprar/${item.id}`, { quantidade });

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Comprar {item.nome}</h3>
        <label className="campo">
          <span>Quantidade</span>
          <input type="number" min={1} max={maximo} value={quantidade}
                 onChange={(e) => setQuantidade(Math.max(1, Math.min(maximo, Number(e.target.value))))} />
        </label>
        <div className="loja__resumo">
          <div><span>Total</span><strong>{brl(total)}</strong></div>
          <div><span>Sobra depois</span>
            <strong className={total > creditos ? 'is-negativo' : ''}>{brl(creditos - total)}</strong>
          </div>
        </div>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={() => { setErro(null); setSalvando(true); }}
                  disabled={salvando || total > creditos}>
            Confirmar compra
          </button>
        </div>
      </div>

      {salvando && (
        <Processo
          etapas={['Separando o item', 'Debitando os créditos']}
          sucesso="Compra concluída."
          trabalho={comprar}
          aoConcluir={onComprado}
          aoFalhar={(m) => { setSalvando(false); setErro(m); }}
        />
      )}
    </div>
  );
}

function ModalItem({ item, onFechar, onSalvo }: {
  item?: Item; onFechar: () => void; onSalvo: () => void;
}) {
  const [f, setF] = useState({
    nome: item?.nome ?? '', descricao: item?.descricao ?? '', categoria: item?.categoria ?? '',
    preco: item ? String(item.preco) : '', estoque: item?.estoque == null ? '' : String(item.estoque),
    ativo: item?.ativo ?? true, imagemBase64: item?.imagemBase64 ?? '',
  });
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      const corpo = {
        nome: f.nome, descricao: f.descricao || undefined, categoria: f.categoria || undefined,
        preco: parseFloat(f.preco.replace(',', '.') || '0'),
        estoque: f.estoque === '' ? null : Number(f.estoque),
        ativo: f.ativo,
        imagemBase64: f.imagemBase64,
      };
      if (item) await api.put(`/loja/itens/${item.id}`, corpo);
      else await api.post('/loja/itens', corpo);
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
      setSalvando(false);
    }
  }

  return (
    <div className="modal__overlay" onClick={onFechar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{item ? 'Editar item' : 'Novo item'}</h3>
        <label className="campo"><span>Nome</span>
          <input value={f.nome} autoFocus onChange={(e) => setF({ ...f, nome: e.target.value })} /></label>
        <label className="campo"><span>Categoria</span>
          <input value={f.categoria} placeholder="Ex.: Acessório, Visual, Equipamento"
                 onChange={(e) => setF({ ...f, categoria: e.target.value })} /></label>
        <label className="campo"><span>Preço (R$)</span>
          <input value={f.preco} inputMode="decimal"
                 onChange={(e) => setF({ ...f, preco: e.target.value })} /></label>
        <label className="campo"><span>Estoque (vazio = ilimitado)</span>
          <input value={f.estoque} inputMode="numeric"
                 onChange={(e) => setF({ ...f, estoque: e.target.value })} /></label>
        <label className="campo"><span>Descrição</span>
          <textarea rows={2} value={f.descricao}
                    onChange={(e) => setF({ ...f, descricao: e.target.value })} /></label>
        <label className="campo">
          <span>Imagem (opcional)</span>
          <input type="file" accept="image/*" onChange={async (e) => {
            const arq = e.target.files?.[0];
            if (!arq) return;
            try { setF({ ...f, imagemBase64: await reduzirImagem(arq, 500) }); }
            catch (err) { setErro(err instanceof Error ? err.message : "Imagem inválida."); }
          }} />
        </label>
        {f.imagemBase64 && (
          <div className="loja__previa">
            <img src={f.imagemBase64} alt="Prévia do item" />
            <button className="btn btn--ghost" onClick={() => setF({ ...f, imagemBase64: "" })}>Remover imagem</button>
          </div>
        )}

        <label className="campo campo--check">
          <input type="checkbox" checked={f.ativo}
                 onChange={(e) => setF({ ...f, ativo: e.target.checked })} />
          <span>À venda</span>
        </label>
        {erro && <div className="modal__erro">{erro}</div>}
        <div className="modal__acoes">
          <button className="btn btn--ghost" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn" onClick={salvar} disabled={salvando || !f.nome || !f.preco}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  );
}

const brl = (v: number) => (v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
