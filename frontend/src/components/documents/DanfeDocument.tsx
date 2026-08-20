import { Documento } from '../../api/tipos';
import './DanfeDocument.css';

const brl = (v?: number) =>
  v == null ? '—' : v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });

export default function DanfeDocument({ doc }: { doc: Documento }) {
  const emissao = new Date(doc.geradoEm).toLocaleString('pt-BR');

  return (
    <div className="danfe">
      <div className="doc-aviso">Documento fictício — simulação — sem valor fiscal</div>
      <div className="danfe__watermark">
        <span>SIMULAÇÃO&nbsp;&nbsp;—&nbsp;&nbsp;SEM&nbsp;&nbsp;VALOR&nbsp;&nbsp;FISCAL</span>
      </div>

      <div className="danfe__canhoto">
        <div className="danfe__canhoto-txt">
          Recebemos de <b>LK TRANSPORTES</b> os produtos e/ou serviços constantes desta nota fictícia.
          Emissão: {emissao} &nbsp; Dest.: {doc.destinatario} &nbsp; Valor: R$ {brl(doc.valorCarga)}
          <div className="danfe__canhoto-sub">
            <div>Data do recebimento</div><div>Identificação e assinatura do recebedor</div>
          </div>
        </div>
        <div className="danfe__canhoto-nfe">
          <div className="danfe__tag">NF</div>
          <div className="danfe__num">Nº {String(doc.numero).padStart(6, '0')}</div>
          <div className="danfe__serie">Série {doc.serie}</div>
        </div>
      </div>

      <div className="danfe__g danfe__head3">
        <div className="danfe__c">
          <div className="danfe__emit-name">LK Transportes</div>
          <div className="danfe__emit-addr">
            Transportadora virtual — Euro Truck Simulator 2<br />
            Mapa RBR · Viagem #{doc.numeroViagem}
          </div>
        </div>
        <div className="danfe__c danfe__danfe-col">
          <div className="danfe__big">NOTA FISCAL</div>
          <div className="danfe__sub">Documento de simulação<br />sem validade fiscal</div>
          <div className="danfe__num">Nº {String(doc.numero).padStart(6, '0')}</div>
          <div className="danfe__serie">Série {doc.serie}</div>
        </div>
        <div className="danfe__c danfe__chave-box">
          <label>Chave interna de simulação</label>
          <div className="danfe__chave-num">{doc.chaveInterna}</div>
          <div className="danfe__barcode" />
          <div className="danfe__chave-note">
            Identificador interno da plataforma. Não é chave fiscal e não possui
            qualquer vínculo com a SEFAZ.
          </div>
        </div>
      </div>

      <div className="danfe__section-bar">Remetente / Destinatário</div>
      <div className="danfe__g danfe__destrow1">
        <div className="danfe__c"><label>Remetente</label><div className="danfe__v">{doc.remetente}</div></div>
        <div className="danfe__c"><label>Destinatário</label><div className="danfe__v">{doc.destinatario}</div></div>
        <div className="danfe__c"><label>Emissão</label><div className="danfe__v danfe__v--sm">{emissao}</div></div>
      </div>

      <div className="danfe__g danfe__g3">
        <div className="danfe__c"><label>Origem</label><div className="danfe__v danfe__v--sm">{doc.origem}</div></div>
        <div className="danfe__c"><label>Destino</label><div className="danfe__v danfe__v--sm">{doc.destino}</div></div>
      </div>

      <div className="danfe__section-bar">Dados da carga</div>
      <table className="danfe__itens">
        <thead>
          <tr><th>Descrição</th><th>Peso (kg)</th><th>Valor da carga</th><th>Valor do frete</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>{doc.carga}</td>
            <td className="num">{brl(doc.pesoKg)}</td>
            <td className="num">{brl(doc.valorCarga)}</td>
            <td className="num">{brl(doc.valorFrete)}</td>
          </tr>
        </tbody>
      </table>

      <div className="danfe__section-bar">Transporte</div>
      <div className="danfe__g danfe__g3">
        <div className="danfe__c">
          <label>Motorista / Veículo</label>
          <div className="danfe__v danfe__v--sm">
            {doc.motorista} · {doc.caminhao} — {doc.placaCaminhao}
            {doc.placaCarreta && <> · Carreta {doc.carreta} — {doc.placaCarreta}</>}
          </div>
        </div>
        <div className="danfe__c">
          <span className="danfe__status-stamp">
            <span className="dot" /> Documento fictício
          </span>
        </div>
      </div>
    </div>
  );
}
