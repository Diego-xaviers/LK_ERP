package com.lktransportes.service;

import com.lktransportes.dto.NovaViagemRequest;
import com.lktransportes.dto.ViagemResponse;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ViagemService {

    private final ViagemRepository viagens;
    private final DocumentoViagemRepository documentos;
    private final UsuarioRepository usuarios;
    private final CaminhaoRepository caminhoes;
    private final CarretaRepository carretas;
    private final DemandaRepository demandas;
    private final ConferenciaService conferencia;
    private final FinanceiroService financeiro;
    private final CnhService cnhs;

    public ViagemService(ViagemRepository viagens, DocumentoViagemRepository documentos,
                         UsuarioRepository usuarios, CaminhaoRepository caminhoes,
                         CarretaRepository carretas, DemandaRepository demandas,
                         ConferenciaService conferencia, FinanceiroService financeiro,
                         CnhService cnhs) {
        this.viagens = viagens;
        this.documentos = documentos;
        this.usuarios = usuarios;
        this.caminhoes = caminhoes;
        this.carretas = carretas;
        this.demandas = demandas;
        this.conferencia = conferencia;
        this.financeiro = financeiro;
        this.cnhs = cnhs;
    }

    /**
     * Viagem avulsa — fora de qualquer demanda.
     *
     * Passa pelas mesmas amarras da demanda (CNH válida, uma viagem por vez,
     * caminhão próprio), senão esta tela seria o caminho fácil para furar todas
     * elas. E, como aqui não existe tarifa de demanda para definir o frete, o
     * valor é digitado — por isso a viagem sai marcada como avulsa e a
     * conferência não a aprova sozinha.
     */
    @Transactional
    public ViagemResponse criarViagem(NovaViagemRequest req) {
        viagens.viagensAbertasDoMotorista(req.motoristaId).stream().findFirst()
                .ifPresent(v -> {
                    throw new IllegalStateException(
                            "Você já tem a viagem #" + v.getNumero() + " em aberto. Finalize antes de criar outra.");
                });

        cnhs.exigirCnhValida(req.motoristaId);

        Caminhao caminhao = caminhoes.findById(req.caminhaoId).orElseThrow();
        if (!caminhao.podeSerUsadoPor(req.motoristaId)) {
            throw new IllegalStateException(
                    "O caminhão %s é de outro motorista.".formatted(caminhao.getPlaca()));
        }

        Viagem v = new Viagem();
        v.setNumero(viagens.ultimoNumero() + 1);
        v.setOrigem(req.origem);
        v.setDestino(req.destino);
        v.setEmpresaRemetente(req.empresaRemetente);
        v.setEmpresaDestinataria(req.empresaDestinataria);
        v.setCarga(req.carga);
        v.setPesoKg(req.pesoKg);
        v.setValorCarga(req.valorCarga);
        v.setValorFrete(req.valorFrete);
        v.setMotorista(usuarios.findById(req.motoristaId).orElseThrow());
        v.setCaminhao(caminhao);
        if (req.carretaId != null) {
            v.setCarreta(carretas.findById(req.carretaId).orElseThrow());
        }
        return ViagemResponse.de(viagens.save(v), List.of());
    }

    /**
     * Gera os documentos de uma vez a partir dos dados da viagem — o motorista
     * não preenche NF, CT-e e MDF-e separadamente.
     */
    @Transactional
    public List<DocumentoViagem> gerarDocumentos(UUID viagemId) {
        Viagem v = viagens.findById(viagemId).orElseThrow();

        List<DocumentoViagem> existentes = documentos.findByViagemId(viagemId);
        if (!existentes.isEmpty()) {
            return existentes; // idempotente: não duplica se clicar duas vezes
        }

        return List.of(
                criarDocumento(v, DocumentoViagem.TipoDocumento.NF),
                criarDocumento(v, DocumentoViagem.TipoDocumento.CTE),
                criarDocumento(v, DocumentoViagem.TipoDocumento.MDFE)
        );
    }

    private DocumentoViagem criarDocumento(Viagem v, DocumentoViagem.TipoDocumento tipo) {
        int numero = documentos.proximoNumero(tipo);
        DocumentoViagem d = DocumentoViagem.gerarDe(v, tipo, numero, gerarChaveInterna(v, tipo, numero));
        return documentos.save(d);
    }

    /**
     * Chave INTERNA de simulação. Prefixo "SIM" e formato próprio, deliberadamente
     * diferente de uma chave fiscal de 44 dígitos, pra não haver confusão.
     */
    private String gerarChaveInterna(Viagem v, DocumentoViagem.TipoDocumento tipo, int numero) {
        String data = v.getCriadaEm().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "SIM-%s-%s-%05d-%04d".formatted(tipo.name(), data, numero, v.getNumero());
    }

    @Transactional
    public ViagemResponse iniciar(UUID id) {
        Viagem v = viagens.findWithEventosById(id).orElseThrow();
        v.iniciar();
        viagens.save(v);
        return ViagemResponse.de(v, documentos.findByViagemId(id));
    }

    @Transactional
    public ViagemResponse finalizar(UUID id, String observacao, Boolean houveAvaria) {
        Viagem v = viagens.findWithEventosById(id).orElseThrow();
        v.finalizar(observacao, houveAvaria);

        // Confere contra a telemetria antes de gravar: é o que decide se a viagem
        // pontua e pode ser paga, ou se fica retida esperando um gestor.
        conferencia.conferir(v);
        viagens.save(v);

        // Aprovada de primeira já entra no caixa; retida só entra se o gestor liberar.
        financeiro.creditarFrete(v);

        // Multa e avaria detectada saem da carteira do motorista.
        cnhs.cobrarDaViagem(v);

        // O que a viagem entregou abate a demanda. Retida não abate agora —
        // abate quando (e se) o gestor liberar, junto com o frete.
        abaterDemanda(v);

        return ViagemResponse.de(v, documentos.findByViagemId(id));
    }

    /**
     * Uma entrega da demanda só conta quando a viagem passa a valer — aprovada
     * na conferência ou liberada pelo gestor. É a mesma condição do frete, e pelo
     * mesmo motivo: senão bastaria finalizar viagens que o jogo nunca confirmou
     * para fechar uma demanda inteira no papel.
     *
     * Chamado nos dois pontos da transição (finalizar e liberar) e, como
     * liberarConferencia() recusa viagem que não está retida, cada viagem passa
     * por aqui uma vez só.
     */
    private void abaterDemanda(Viagem v) {
        if (v.getDemanda() == null) return;
        if (!v.liberadaParaPagamento()) return;
        v.getDemanda().registrarEntrega(v.getPesoKg());
        demandas.save(v.getDemanda());
    }

    // ---------- Leituras ----------
    // Todas anotadas com @Transactional(readOnly = true): a montagem do DTO
    // percorre a lista de eventos, e isso precisa acontecer com a sessão aberta.

    /**
     * A viagem que o motorista tem em mãos agora.
     *
     * Devolve a EM_ANDAMENTO; não havendo, devolve a que ainda está CRIADA.
     * Sem isso, quem acabou de pegar uma carga na Logística caía numa tela
     * vazia — a viagem existia, mas ainda não tinha sido iniciada.
     */
    @Transactional(readOnly = true)
    public ViagemResponse viagemAtiva(UUID motoristaId) {
        return viagens.findByMotoristaIdAndStatus(motoristaId, StatusViagem.EM_ANDAMENTO)
                .or(() -> viagens.findByMotoristaIdAndStatus(motoristaId, StatusViagem.CRIADA))
                .map(v -> ViagemResponse.de(v, documentos.findByViagemId(v.getId())))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ViagemResponse> listarTodas() {
        return viagens.buscarTodasComEventos().stream()
                .map(v -> ViagemResponse.de(v, documentos.findByViagemId(v.getId())))
                .toList();
    }

    /** Resumo das viagens de todo mundo — o mural interno da empresa. */
    @Transactional(readOnly = true)
    public List<com.lktransportes.dto.ViagemResumoResponse> feedDaEmpresa() {
        return viagens.buscarTodasComEventos().stream()
                .sorted(java.util.Comparator.comparing(com.lktransportes.model.Viagem::getCriadaEm).reversed())
                .map(com.lktransportes.dto.ViagemResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ViagemResponse> historicoDo(UUID motoristaId) {
        return viagens.findByMotoristaIdOrderByCriadaEmDesc(motoristaId).stream()
                .map(v -> ViagemResponse.de(v, documentos.findByViagemId(v.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ViagemResponse buscar(UUID id) {
        Viagem v = viagens.findWithEventosById(id).orElseThrow();
        return ViagemResponse.de(v, documentos.findByViagemId(id));
    }

    public List<DocumentoViagem> documentosDa(UUID viagemId) {
        return documentos.findByViagemId(viagemId);
    }
    /** Viagens seguradas pela conferência, esperando decisão do gestor. */
    @Transactional(readOnly = true)
    public List<ViagemResponse> retidas() {
        return viagens.findByConferenciaOrderByFinalizadaEmDesc(Viagem.Conferencia.RETIDA).stream()
                .map(v -> ViagemResponse.de(v, documentos.findByViagemId(v.getId())))
                .toList();
    }

    /** O gestor assume a viagem apesar da divergência: volta a pontuar e a poder ser paga. */
    @Transactional
    public ViagemResponse liberar(UUID id, Usuario gestor, String observacao) {
        Viagem v = viagens.findWithEventosById(id).orElseThrow();
        v.liberarConferencia(gestor, observacao);
        viagens.save(v);
        financeiro.creditarFrete(v);
        // A carga estava segurada como reserva enquanto esperava decisão;
        // agora que a viagem vale, ela vira entrega e abate a demanda.
        abaterDemanda(v);
        return ViagemResponse.de(v, documentos.findByViagemId(id));
    }
}
