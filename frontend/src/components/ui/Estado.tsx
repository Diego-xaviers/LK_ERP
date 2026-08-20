import Icon from './Icon';
import './Estado.css';

export function Carregando({ texto = 'Carregando...' }: { texto?: string }) {
  return (
    <div className="estado">
      <span className="estado__spinner" />
      <span>{texto}</span>
    </div>
  );
}

export function Erro({ mensagem, aoTentarNovamente }: { mensagem: string; aoTentarNovamente?: () => void }) {
  return (
    <div className="estado estado--erro">
      <Icon name="alert" size={20} />
      <span>{mensagem}</span>
      {aoTentarNovamente && (
        <button onClick={aoTentarNovamente}>Tentar novamente</button>
      )}
    </div>
  );
}

export function Vazio({ titulo, descricao, acao }: { titulo: string; descricao?: string; acao?: React.ReactNode }) {
  return (
    <div className="estado estado--vazio">
      <strong>{titulo}</strong>
      {descricao && <span>{descricao}</span>}
      {acao}
    </div>
  );
}
