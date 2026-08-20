import { useApi } from '../../hooks/useApi';
import { Aviso } from '../../api/tipos';
import Icon from '../ui/Icon';
import './MuralAvisos.css';

const ICONE = { INFORMATIVO: 'megaphone', ALERTA: 'alert', EVENTO: 'flag' } as const;
const CLASSE = { INFORMATIVO: 'informativo', ALERTA: 'alerta', EVENTO: 'evento' } as const;

const quando = (iso: string) => {
  const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (dias === 0) return 'hoje';
  if (dias === 1) return 'ontem';
  if (dias < 7) return `há ${dias} dias`;
  return new Date(iso).toLocaleDateString('pt-BR');
};

export default function MuralAvisos() {
  const { dados: avisos, carregando } = useApi<Aviso[]>('/avisos');

  if (carregando || !avisos?.length) return null;

  const fixado = avisos.find((a) => a.fixado);
  const demais = avisos.filter((a) => !a.fixado);

  return (
    <div className="mural">
      {fixado && (
        <div className={'banner banner--' + CLASSE[fixado.tipo]}>
          <span className="banner__icone"><Icon name={ICONE[fixado.tipo]} size={18} /></span>
          <div className="banner__texto">
            <strong>{fixado.titulo}</strong>
            <span>{fixado.mensagem}</span>
          </div>
          <span className="banner__pin" title="Fixado pelo administrador"><Icon name="pin" size={14} /></span>
        </div>
      )}

      {demais.length > 0 && (
        <div className="mural__card">
          <header className="mural__head">
            <h2>Mural da transportadora</h2>
            <span className="mural__sub">{avisos.length} avisos</span>
          </header>
          <ul className="mural__lista">
            {demais.map((a) => (
              <li className="mural__item" key={a.id}>
                <span className={'mural__icone mural__icone--' + CLASSE[a.tipo]}>
                  <Icon name={ICONE[a.tipo]} size={15} />
                </span>
                <div className="mural__conteudo">
                  <strong>{a.titulo}</strong>
                  <p>{a.mensagem}</p>
                </div>
                <time>{quando(a.publicadoEm)}</time>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
