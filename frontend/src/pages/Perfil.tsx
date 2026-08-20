import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import { useUsuario } from '../auth';
import { useApi } from '../hooks/useApi';
import { Carregando, Erro } from '../components/ui/Estado';
import SignaturePad from '../components/SignaturePad';
import Icon from '../components/ui/Icon';
import { Perfil as PerfilDados } from '../api/tipos';
import './Perfil.css';

/** Acima disso o base64 estoura o limite do servidor — a foto é reduzida antes de subir. */
const LADO_MAXIMO = 400;

export default function Perfil() {
  const usuario = useUsuario();
  const { dados, carregando, erro, recarregar } = useApi<PerfilDados>(`/perfil/${usuario.id}`);
  const [form, setForm] = useState<PerfilDados | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const [problema, setProblema] = useState<string | null>(null);
  const [assinando, setAssinando] = useState(false);
  const arquivoRef = useRef<HTMLInputElement>(null);

  useEffect(() => { if (dados) setForm(dados); }, [dados]);

  if (carregando && !form) return <Carregando texto="Carregando seu perfil..." />;
  if (erro) return <Erro mensagem={erro} aoTentarNovamente={recarregar} />;
  if (!form) return null;

  function campo(chave: keyof PerfilDados, valor: string) {
    setForm((f) => (f ? { ...f, [chave]: valor } : f));
    setAviso(null);
  }

  /**
   * Reduz a imagem no navegador antes de mandar. Uma foto de celular vira
   * ~4 MB em base64 e o servidor recusa — aqui ela sai com no máximo 400px.
   */
  async function escolherFoto(arquivo: File) {
    setProblema(null);
    if (!arquivo.type.startsWith('image/')) {
      setProblema('Escolha um arquivo de imagem.');
      return;
    }
    const img = new Image();
    const url = URL.createObjectURL(arquivo);
    img.onload = () => {
      const escala = Math.min(1, LADO_MAXIMO / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * escala);
      canvas.height = Math.round(img.height * escala);
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      campo('fotoBase64', canvas.toDataURL('image/jpeg', 0.85));
      URL.revokeObjectURL(url);
    };
    img.onerror = () => { setProblema('Não foi possível ler essa imagem.'); URL.revokeObjectURL(url); };
    img.src = url;
  }

  async function salvar() {
    setSalvando(true);
    setProblema(null);
    setAviso(null);
    try {
      const salvo = await api.put<PerfilDados>(`/perfil/${usuario.id}`, form);
      setForm(salvo);
      setAviso('Perfil salvo.');
    } catch (e) {
      setProblema(e instanceof ApiError ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="perfil">
      <header className="perfil__head">
        <div>
          <h1>Meu perfil</h1>
          <p>Estes dados são seus e só você e a gestão enxergam</p>
        </div>
        <span className={'perfil__selo' + (form.prontoParaCnh ? ' is-ok' : '')}>
          <Icon name={form.prontoParaCnh ? 'check' : 'alertCircle'} size={14} />
          {form.prontoParaCnh ? 'Pronto para emitir CNH' : 'Falta nome completo e nascimento'}
        </span>
      </header>

      <div className="perfil__grid">
        <section className="perfil__card perfil__retrato">
          <h2>Retrato</h2>
          <div className="perfil__foto">
            {form.fotoBase64
              ? <img src={form.fotoBase64} alt="Foto do motorista" />
              : <span className="perfil__foto-vazia"><Icon name="users" size={28} /></span>}
          </div>
          <input ref={arquivoRef} type="file" accept="image/*" hidden
                 onChange={(e) => e.target.files?.[0] && escolherFoto(e.target.files[0])} />
          <div className="perfil__foto-acoes">
            <button className="btn btn--ghost" onClick={() => arquivoRef.current?.click()}>
              {form.fotoBase64 ? 'Trocar foto' : 'Enviar foto'}
            </button>
            {form.fotoBase64 && (
              <button className="btn btn--ghost" onClick={() => campo('fotoBase64', '')}>Remover</button>
            )}
          </div>
          <p className="perfil__dica">É a foto que aparece na sua CNH. Use um enquadramento 3x4.</p>

          <h2 className="perfil__sub">Assinatura</h2>
          {assinando ? (
            <>
              <SignaturePad onChange={(url) => campo('assinaturaBase64', url ?? '')} />
              <button className="btn btn--ghost" onClick={() => setAssinando(false)}>Concluir</button>
            </>
          ) : (
            <>
              <div className="perfil__assinatura">
                {form.assinaturaBase64
                  ? <img src={form.assinaturaBase64} alt="Assinatura" />
                  : <span>Sem assinatura</span>}
              </div>
              <button className="btn btn--ghost" onClick={() => setAssinando(true)}>
                {form.assinaturaBase64 ? 'Assinar de novo' : 'Assinar'}
              </button>
            </>
          )}
        </section>

        <div className="perfil__coluna">
          <Bloco titulo="Identificação" nota="Vai impresso na CNH">
            <Campo label="Nome completo" valor={form.nomeCompleto} ao={(v) => campo('nomeCompleto', v)} largo />
            <Campo label="Data de nascimento" tipo="date" valor={form.dataNascimento} ao={(v) => campo('dataNascimento', v)} />
            <Campo label="CPF" valor={form.cpf} ao={(v) => campo('cpf', v)} />
            <Campo label="RG" valor={form.rg} ao={(v) => campo('rg', v)} />
            <Campo label="Órgão emissor" valor={form.orgaoEmissor} ao={(v) => campo('orgaoEmissor', v)} />
            <Campo label="UF emissor" max={2} valor={form.ufEmissor} ao={(v) => campo('ufEmissor', v)} />
            <Campo label="Nome da mãe" valor={form.nomeMae} ao={(v) => campo('nomeMae', v)} largo />
            <Campo label="Nome do pai" valor={form.nomePai} ao={(v) => campo('nomePai', v)} largo />
            <Campo label="Naturalidade" valor={form.naturalidadeCidade} ao={(v) => campo('naturalidadeCidade', v)} />
            <Campo label="UF de nascimento" max={2} valor={form.naturalidadeUf} ao={(v) => campo('naturalidadeUf', v)} />
          </Bloco>

          <Bloco titulo="Contato e residência">
            <Campo label="Telefone" valor={form.telefone} ao={(v) => campo('telefone', v)} />
            <Campo label="CEP" valor={form.cep} ao={(v) => campo('cep', v)} />
            <Campo label="Endereço" valor={form.endereco} ao={(v) => campo('endereco', v)} largo />
            <Campo label="Cidade" valor={form.cidade} ao={(v) => campo('cidade', v)} />
            <Campo label="UF" max={2} valor={form.estado} ao={(v) => campo('estado', v)} />
          </Bloco>

          <Bloco titulo="Na transportadora">
            <Campo label="Apelido no jogo" valor={form.apelido} ao={(v) => campo('apelido', v)} />
            <Campo label="Steam ID" valor={form.steamId} ao={(v) => campo('steamId', v)} />
            <Campo label="Discord" valor={form.discord} ao={(v) => campo('discord', v)} />
            <label className="campo campo--largo">
              <span>Sobre você</span>
              <textarea rows={3} maxLength={600} value={form.sobre ?? ''}
                        onChange={(e) => campo('sobre', e.target.value)} />
            </label>
          </Bloco>
        </div>
      </div>

      <div className="perfil__rodape">
        {problema && <span className="perfil__erro">{problema}</span>}
        {aviso && <span className="perfil__ok"><Icon name="check" size={14} /> {aviso}</span>}
        <button className="btn" onClick={salvar} disabled={salvando}>
          {salvando ? 'Salvando...' : 'Salvar perfil'}
        </button>
      </div>
    </div>
  );
}

function Bloco({ titulo, nota, children }: { titulo: string; nota?: string; children: React.ReactNode }) {
  return (
    <section className="perfil__card">
      <h2>{titulo}{nota && <small>{nota}</small>}</h2>
      <div className="perfil__campos">{children}</div>
    </section>
  );
}

function Campo({ label, valor, ao, tipo = 'text', largo, max }: {
  label: string; valor?: string; ao: (v: string) => void;
  tipo?: string; largo?: boolean; max?: number;
}) {
  return (
    <label className={'campo' + (largo ? ' campo--largo' : '')}>
      <span>{label}</span>
      <input type={tipo} value={valor ?? ''} maxLength={max} onChange={(e) => ao(e.target.value)} />
    </label>
  );
}
