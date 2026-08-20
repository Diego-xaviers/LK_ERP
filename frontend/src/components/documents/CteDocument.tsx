import { Documento } from '../../api/tipos';
import './CteDocument.css';

const brl = (v?: number) =>
  v == null ? '—' : v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });

export default function CteDocument({ doc }: { doc: Documento }) {
  const emissao = new Date(doc.geradoEm).toLocaleString('pt-BR');

  return (
    <div className="cte">
      <div className="doc-aviso">Documento fictício — simulação — sem valor fiscal</div>
      <div className="cte__watermark">
        <span>SIMULAÇÃO&nbsp;&nbsp;—&nbsp;&nbsp;SEM&nbsp;&nbsp;VALOR&nbsp;&nbsp;FISCAL</span>
      </div>

      <div className="cte__recibo">
        <div className="cte__recibo-decl">
          DECLARO QUE RECEBI OS VOLUMES DESTE CONHECIMENTO EM PERFEITO ESTADO PELO QUE DOU POR CUMPRIDO O PRESENTE CONTRATO DE TRANSPORTE
        </div>
        <div className="cte__recibo-nome">
          <label>Nome</label><div className="cte__line" />
          <label>Assinatura / Carimbo</label>
        </div>
        <div className="cte__recibo-tag">
          <div className="cte__tag">CT-e</div>
          <div className="cte__num">Nº {String(doc.numero).padStart(6, '0')}</div>
          <div className="cte__serie">Série {doc.serie}</div>
        </div>
      </div>

      <div className="cte__g cte__head3">
        <div className="cte__c">
          <div className="cte__emit-name">LK Transportes</div>
          <div className="cte__emit-addr">
            Transportadora virtual — ETS2 / Mapa RBR<br />Viagem #{doc.numeroViagem}
          </div>
        </div>
        <div className="cte__c cte__dacte-col">
          <div className="cte__big">CONHECIMENTO DE TRANSPORTE</div>
          <div className="cte__sub">Documento de simulação — sem validade fiscal</div>
          <div className="cte__modal-pill">MODAL RODOVIÁRIO</div>
        </div>
        <div className="cte__c">
          <label>Chave interna de simulação</label>
          <div className="cte__chave-num">{doc.chaveInterna}</div>
        </div>
      </div>
      <div className="cte__barcode" />

      <div className="cte__g cte__row2">
        <div className="cte__c"><label>Início da prestação</label><div className="cte__v">{doc.origem}</div></div>
        <div className="cte__c"><label>Término da prestação</label><div className="cte__v">{doc.destino}</div></div>
      </div>
      <div className="cte__g cte__row2">
        <div className="cte__c"><label>Remetente</label><div className="cte__v">{doc.remetente}</div></div>
        <div className="cte__c"><label>Destinatário</label><div className="cte__v">{doc.destinatario}</div></div>
      </div>

      <div className="cte__section-bar">Detalhamento da carga</div>
      <div className="cte__g cte__row2">
        <div className="cte__c"><label>Produto predominante</label><div className="cte__v danfe__v--sm">{doc.carga}</div></div>
        <div className="cte__c"><label>Peso bruto (kg)</label><div className="cte__v">{brl(doc.pesoKg)}</div></div>
      </div>

      <div className="cte__section-bar">Valor da prestação de serviço</div>
      <table className="cte__comp">
        <thead><tr><th>Descrição</th><th style={{ textAlign: 'right' }}>Valor (R$)</th></tr></thead>
        <tbody>
          <tr><td>Frete</td><td className="num">{brl(doc.valorFrete)}</td></tr>
          <tr><td>Valor da mercadoria transportada</td><td className="num">{brl(doc.valorCarga)}</td></tr>
        </tbody>
      </table>
      <div className="cte__total-row">
        <span>Valor total do serviço <b>R$ {brl(doc.valorFrete)}</b></span>
      </div>

      <div className="cte__section-bar">Dados do modal rodoviário</div>
      <div className="cte__g cte__row2">
        <div className="cte__c"><label>Veículo</label>
          <div className="cte__v">{doc.caminhao} — {doc.placaCaminhao}</div></div>
        <div className="cte__c"><label>Condutor</label><div className="cte__v">{doc.motorista}</div></div>
      </div>
    </div>
  );
}
