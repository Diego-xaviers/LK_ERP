import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';
import './Processo.css';

/** Tempo de cada etapa na tela. Curto de propósito: é ritmo, não espera. */
const PASSO_MS = 700;
/** Quanto a mensagem final fica visível antes de devolver o controle. */
const FINAL_MS = 900;

type Estado = 'rodando' | 'sucesso' | 'erro';

/**
 * Overlay de etapas — "Emitindo Nota Fiscal", "Emitindo CT-e"...
 *
 * A encenação é só o ritmo: o trabalho de verdade começa junto com a primeira
 * etapa e roda em paralelo. A tela de sucesso só aparece quando a animação
 * terminou **e** a promessa resolveu — se a chamada falhar, o overlay corta
 * direto para o erro. Nunca se anuncia um documento que o servidor não emitiu.
 *
 * Se o servidor demorar mais que a animação, a barra segura em 92% na última
 * etapa em vez de fingir que acabou.
 */
export default function Processo<T>({
  etapas, sucesso, trabalho, aoConcluir, aoFalhar,
}: {
  etapas: string[];
  sucesso: string;
  trabalho: () => Promise<T>;
  aoConcluir: (resultado: T) => void;
  aoFalhar: (mensagem: string) => void;
}) {
  const [passo, setPasso] = useState(0);
  const [estado, setEstado] = useState<Estado>('rodando');
  const [erro, setErro] = useState('');

  // Em refs para o efeito rodar uma vez só, mesmo se o pai re-renderizar.
  const vivos = useRef({ trabalho, aoConcluir, aoFalhar, etapas });
  vivos.current = { trabalho, aoConcluir, aoFalhar, etapas };

  /**
   * A promessa do trabalho fica num ref, criada uma única vez.
   *
   * Sem isso o `StrictMode` — que em desenvolvimento monta, desmonta e monta o
   * componente de novo — dispararia a chamada DUAS vezes: duas viagens criadas,
   * dois acertos pagos. O ref sobrevive ao ciclo, então a segunda passagem do
   * efeito só se pendura na promessa que já está rodando.
   */
  const emCurso = useRef<Promise<T> | null>(null);

  useEffect(() => {
    let montado = true;
    const timers: number[] = [];
    const { etapas: lista } = vivos.current;

    if (!emCurso.current) emCurso.current = vivos.current.trabalho();

    let resultado: T;
    const trabalhoPronto = emCurso.current.then(
      (r) => { resultado = r; },
      (e) => {
        if (!montado) return Promise.reject(e);
        setEstado('erro');
        setErro(e instanceof Error ? e.message : 'Não foi possível concluir.');
        return Promise.reject(e);
      },
    );
    // A rejeição já foi tratada acima; este catch só evita o aviso de promessa solta.
    trabalhoPronto.catch(() => {});

    for (let i = 1; i < lista.length; i++) {
      timers.push(window.setTimeout(() => montado && setPasso(i), i * PASSO_MS));
    }
    const animacaoPronta = new Promise<void>((resolver) => {
      timers.push(window.setTimeout(resolver, lista.length * PASSO_MS));
    });

    Promise.all([trabalhoPronto, animacaoPronta])
      .then(() => {
        if (!montado) return;
        setEstado('sucesso');
        timers.push(window.setTimeout(() => {
          if (montado) vivos.current.aoConcluir(resultado);
        }, FINAL_MS));
      })
      .catch(() => {});

    return () => { montado = false; timers.forEach(clearTimeout); };
  }, []);

  const largura = estado === 'sucesso'
    ? 100
    : Math.min(((passo + 1) / etapas.length) * 100, 92);

  return (
    <div className="processo__fundo">
      <div className={'processo is-' + estado} role="status" aria-live="polite">
        {estado === 'erro' ? (
          <>
            <span className="processo__icone processo__icone--erro">
              <Icon name="alert" size={22} />
            </span>
            <strong>Não deu certo</strong>
            <p>{erro}</p>
            <button className="btn" onClick={() => vivos.current.aoFalhar(erro)}>Fechar</button>
          </>
        ) : estado === 'sucesso' ? (
          <>
            <span className="processo__icone processo__icone--ok">
              <Icon name="check" size={22} strokeWidth={2.5} />
            </span>
            <strong>{sucesso}</strong>
            <div className="processo__barra"><i style={{ width: '100%' }} /></div>
          </>
        ) : (
          <>
            <span className="processo__spinner" />
            <strong>{etapas[passo]}</strong>
            <div className="processo__barra"><i style={{ width: `${largura}%` }} /></div>
            <ol className="processo__lista">
              {etapas.map((e, i) => (
                <li key={e} className={i < passo ? 'is-feito' : i === passo ? 'is-atual' : ''}>
                  {i < passo ? <Icon name="check" size={12} strokeWidth={2.5} /> : <span className="processo__ponto" />}
                  {e}
                </li>
              ))}
            </ol>
          </>
        )}
      </div>
    </div>
  );
}
