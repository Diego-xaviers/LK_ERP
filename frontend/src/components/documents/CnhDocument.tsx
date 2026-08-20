import './CnhDocument.css';

export interface CnhDados {
  numeroRegistro: string;
  categoria: string;
  primeiraHabilitacao?: string;
  validade: string;
  pontos: number;
  pontosIniciais: number;
  estado: 'ATIVA' | 'VENCIDA' | 'SUSPENSA';
  valida: boolean;
  motivoBloqueio?: string;
  emitidaEm: string;
  emitidaPor?: string;
  observacoes?: string;
  nome: string;
  dataNascimento?: string;
  cpf?: string;
  rg?: string;
  orgaoEmissor?: string;
  ufEmissor?: string;
  nomeMae?: string;
  nomePai?: string;
  naturalidade?: string;
  naturalidadeUf?: string;
  fotoBase64?: string;
  assinaturaBase64?: string;
}

/**
 * Carteira desenhada com os dados reais do perfil — foto e assinatura vêm de lá,
 * não são cópias guardadas na CNH, pra não divergirem quando o motorista trocar.
 *
 * Documento de roleplay: o rodapé diz isso em letras claras de propósito.
 */
export default function CnhDocument({ dados }: { dados: CnhDados }) {
  return (
    <div className={'cnh cnh--' + dados.estado.toLowerCase()}>
      <header className="cnh__topo">
        <div>
          <strong>CARTEIRA NACIONAL DE HABILITAÇÃO</strong>
          <span>LK Transportes · documento de simulação</span>
        </div>
        <span className={'cnh__selo is-' + dados.estado.toLowerCase()}>{dados.estado}</span>
      </header>

      <div className="cnh__corpo">
        <div className="cnh__coluna-foto">
          <div className="cnh__foto">
            {dados.fotoBase64
              ? <img src={dados.fotoBase64} alt="Foto do condutor" />
              : <span>sem foto</span>}
          </div>
          <div className="cnh__assinatura">
            {dados.assinaturaBase64
              ? <img src={dados.assinaturaBase64} alt="Assinatura do condutor" />
              : <span>sem assinatura</span>}
            <em>assinatura do condutor</em>
          </div>
        </div>

        <div className="cnh__dados">
          <Campo rotulo="Nome" valor={dados.nome} largo />
          <Campo rotulo="Doc. identidade / órgão / UF"
                 valor={[dados.rg, dados.orgaoEmissor, dados.ufEmissor].filter(Boolean).join(' · ')} />
          <Campo rotulo="CPF" valor={dados.cpf} />
          <Campo rotulo="Data de nascimento" valor={data(dados.dataNascimento)} />
          <Campo rotulo="Naturalidade"
                 valor={[dados.naturalidade, dados.naturalidadeUf].filter(Boolean).join(' — ')} />
          <Campo rotulo="Filiação" valor={[dados.nomeMae, dados.nomePai].filter(Boolean).join(' / ')} largo />
          <Campo rotulo="Nº registro" valor={dados.numeroRegistro} />
          <Campo rotulo="Categoria" valor={dados.categoria} destaque />
          <Campo rotulo="1ª habilitação" valor={data(dados.primeiraHabilitacao)} />
          <Campo rotulo="Validade" valor={data(dados.validade)} destaque={dados.estado !== 'VENCIDA'} />
        </div>
      </div>

      <div className="cnh__pontos">
        <div className="cnh__pontos-cabeca">
          <span>Pontuação na carteira</span>
          <strong>{dados.pontos} de {dados.pontosIniciais}</strong>
        </div>
        <div className="cnh__pontos-barra">
          <i style={{ width: `${(dados.pontos / dados.pontosIniciais) * 100}%` }}
             className={dados.pontos <= 5 ? 'is-critico' : ''} />
        </div>
        {dados.motivoBloqueio && <p className="cnh__bloqueio">{dados.motivoBloqueio}</p>}
      </div>

      <footer className="cnh__rodape">
        Documento fictício, gerado para roleplay na LK Transportes.
        Não possui validade legal nem vínculo com o DETRAN.
      </footer>
    </div>
  );
}

function Campo({ rotulo, valor, largo, destaque }: {
  rotulo: string; valor?: string; largo?: boolean; destaque?: boolean;
}) {
  return (
    <div className={'cnh__campo' + (largo ? ' is-largo' : '') + (destaque ? ' is-destaque' : '')}>
      <span>{rotulo}</span>
      <strong>{valor && valor.length ? valor : '—'}</strong>
    </div>
  );
}

function data(iso?: string) {
  if (!iso) return undefined;
  return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR');
}
