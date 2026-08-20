import { Documento } from '../../api/tipos';
import './MdfeDocument.css';

const brl = (v?: number) =>
  v == null ? '—' : v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });

export default function MdfeDocument({ doc }: { doc: Documento }) {
  const emissao = new Date(doc.geradoEm).toLocaleString('pt-BR');

  return (
    <div className="mdfe">
      <div className="doc-aviso">Documento fictício — simulação — sem valor fiscal</div>
      <div className="mdfe__watermark">
        <span>SIMULAÇÃO&nbsp;&nbsp;—&nbsp;&nbsp;SEM&nbsp;&nbsp;VALOR&nbsp;&nbsp;FISCAL</span>
      </div>

      <div className="mdfe__g mdfe__head3">
        <div className="mdfe__c">
          <div className="mdfe__emit-name">LK Transportes</div>
          <div className="mdfe__emit-addr">ETS2 / Mapa RBR · Viagem #{doc.numeroViagem}</div>
        </div>
        <div className="mdfe__c mdfe__col">
          <div className="mdfe__big">MANIFESTO DE TRANSPORTE</div>
          <div className="mdfe__sub">Documento de simulação — sem validade fiscal</div>
          <div className="mdfe__uf-pill">{doc.origem} → {doc.destino}</div>
        </div>
        <div className="mdfe__c mdfe__status-col">
          <span className="mdfe__status-stamp"><span className="dot" /> Fictício</span>
        </div>
      </div>
      <div className="mdfe__barcode" />
      <div className="mdfe__chave-num">{doc.chaveInterna}</div>

      <div className="mdfe__section-bar">Veículo e condutor</div>
      <div className="mdfe__g mdfe__row2">
        <div className="mdfe__c"><label>Caminhão</label>
          <div className="mdfe__v">{doc.caminhao} — {doc.placaCaminhao}</div></div>
        <div className="mdfe__c"><label>Condutor</label><div className="mdfe__v">{doc.motorista}</div></div>
      </div>
      {doc.placaCarreta && (
        <div className="mdfe__g mdfe__row2">
          <div className="mdfe__c"><label>Carreta</label><div className="mdfe__v">{doc.carreta}</div></div>
          <div className="mdfe__c"><label>Placa da carreta</label><div className="mdfe__v">{doc.placaCarreta}</div></div>
        </div>
      )}

      <div className="mdfe__section-bar">Carga manifestada</div>
      <table className="mdfe__list">
        <thead><tr><th>Descrição</th><th>Peso bruto (kg)</th><th>Valor da carga</th></tr></thead>
        <tbody>
          <tr>
            <td>{doc.carga}</td>
            <td>{brl(doc.pesoKg)}</td>
            <td>R$ {brl(doc.valorCarga)}</td>
          </tr>
        </tbody>
      </table>

      <div className="mdfe__section-bar">Percurso</div>
      <div className="mdfe__g mdfe__row2">
        <div className="mdfe__c"><label>Município de carregamento</label><div className="mdfe__v">{doc.origem}</div></div>
        <div className="mdfe__c"><label>Município de descarga</label><div className="mdfe__v">{doc.destino}</div></div>
      </div>

      <div className="mdfe__section-bar">Informações adicionais</div>
      <div className="mdfe__g" style={{ gridTemplateColumns: '1fr' }}>
        <div className="mdfe__c">
          <div className="mdfe__v mdfe__v--mono" style={{ fontSize: '9px' }}>
            Gerado em {emissao} — ambiente de simulação da LK Transportes.
          </div>
        </div>
      </div>
    </div>
  );
}
