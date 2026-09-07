package com.lktransportes.service;

import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.dto.TelemetriaPing.MultaAgente;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class MultasConfiaveisTest {
    @Autowired MultasService multas;
    @Autowired TelemetriaService telemetria;
    @Autowired VtlogService vtlog;
    @Autowired UsuarioRepository usuarios;
    @Autowired ViagemRepository viagens;
    @Autowired EventoViagemRepository eventos;
    @Autowired CaminhaoRepository caminhoes;
    @Autowired PerfilRepository perfis;
    @Autowired PlatformTransactionManager transactions;
    Usuario motorista;
    Viagem viagem;
    String steam;

    @BeforeEach void preparar() {
        motorista = new Usuario(); motorista.setNome("Auditoria multas");
        motorista.setEmail(UUID.randomUUID()+"@teste.lk"); motorista.setSenhaHash("teste");
        motorista.setPapel(Usuario.Papel.MOTORISTA); motorista.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
        motorista = usuarios.save(motorista);
        steam = UUID.randomUUID().toString();
        Perfil perfil = new Perfil(); perfil.setUsuario(motorista); perfil.setSteamId(steam); perfis.save(perfil);
        viagem = novaViagem();
    }
    Viagem novaViagem() {
        Viagem v = new Viagem(); v.setNumero(viagens.ultimoNumero()+1); v.setMotorista(motorista);
        v.setCaminhao(caminhoes.findAll().getFirst()); v.setOrigem("Sinop"); v.setDestino("Cuiabá");
        v.setEmpresaRemetente("Origem"); v.setEmpresaDestinataria("Destino"); v.setCarga("Soja");
        v.setPesoKg(new BigDecimal("25000")); v.setAgenteJobId(UUID.randomUUID()); v.iniciar();
        return viagens.save(v);
    }
    MultaAgente multa(String valor) { return new MultaAgente(UUID.randomUUID(), viagem.getAgenteJobId(), new BigDecimal(valor), Instant.now()); }
    BigDecimal total() { return viagens.findWithEventosById(viagem.getId()).orElseThrow().totalDespesas(); }
    void conferir(String valor) {
        new TransactionTemplate(transactions).executeWithoutResult(s -> multas.conferir(viagens.findById(viagem.getId()).orElseThrow(), valor == null ? null : new BigDecimal(valor)));
    }
    VtlogService.EntregaVtlog entrega(String job, String valor) {
        return new VtlogService.EntregaVtlog(job, steam, "Sinop", "Cuiabá", "Origem", "Destino", "Soja", new BigDecimal("25000"), 100d, 30d, 0d, null, valor == null ? null : new BigDecimal(valor), System.currentTimeMillis()-60000, System.currentTimeMillis());
    }

    @Test void recibosRepetidosMesmoValorERestart() {
        var a=multa("100"); var b=multa("100"); var c=multa("100");
        assertThat(multas.receber(motorista.getId(), List.of(a,b,c))).containsExactly(a.id(),b.id(),c.id());
        new TransactionTemplate(transactions).executeWithoutResult(s -> new MultasService(eventos,viagens,usuarios).receber(motorista.getId(),List.of(a,b,c)));
        assertThat(total()).isEqualByComparingTo("300");
        assertThat(eventos.findByViagemId(viagem.getId())).hasSize(3);
    }
    @Test void doisReenviosSimultaneosNaoDuplicam() throws Exception {
        var a=multa("200");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<List<UUID>> envio=()->{ start.await(); return multas.receber(motorista.getId(),List.of(a)); };
            var f1=pool.submit(envio); var f2=pool.submit(envio); start.countDown();
            assertThat(f1.get(10,TimeUnit.SECONDS)).contains(a.id());
            assertThat(f2.get(10,TimeUnit.SECONDS)).contains(a.id());
        }
        assertThat(total()).isEqualByComparingTo("200");
    }
    @Test void perdaDaRespostaMantemMesmaConfirmacao() {
        var a=multa("80"); var primeira=multas.receber(motorista.getId(),List.of(a));
        assertThat(multas.receber(motorista.getId(),List.of(a))).isEqualTo(primeira);
        assertThat(total()).isEqualByComparingTo("80");
    }
    @Test void falhaNoLoteFazRollbackDosRecibos() {
        var a=multa("90"); var invalida=multa("-1");
        assertThatThrownBy(()->multas.receber(motorista.getId(),List.of(a,invalida))).isInstanceOf(IllegalArgumentException.class);
        assertThat(total()).isZero();
        assertThat(multas.receber(motorista.getId(),List.of(a))).contains(a.id());
    }
    @Test void pendenteSemVinculoNaoEhConfirmadaOuDesviada() {
        var a=new MultaAgente(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("90"),Instant.now());
        assertThat(multas.receber(motorista.getId(),List.of(a))).isEmpty();
        assertThat(total()).isZero();
    }
    @Test void naoAceitaViagemDeOutroMotorista() {
        assertThatThrownBy(()->multas.receber(usuarios.findByEmail("admin@lk.com").orElseThrow().getId(), List.of(multa("10"))))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void naoReutilizaIdComValorDiferente() {
        var a=multa("10"); multas.receber(motorista.getId(),List.of(a));
        assertThatThrownBy(()->multas.receber(motorista.getId(),List.of(new MultaAgente(a.id(),a.agenteJobId(),new BigDecimal("20"),a.ocorridoEm()))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(total()).isEqualByComparingTo("10");
    }
    @Test void vtlogComplementaSomenteDiferencaEReenvioNaoSoma() {
        multas.receber(motorista.getId(),List.of(multa("400")));
        conferir("600"); conferir("600");
        assertThat(total()).isEqualByComparingTo("600");
        assertThat(eventos.findByViagemId(viagem.getId()).stream().filter(e->e instanceof Multa m && m.isAjusteVtlog()).toList())
            .singleElement().satisfies(e->assertThat(e.getValor()).isEqualByComparingTo("200"));
    }
    @Test void reciboAtrasadoSubstituiAjusteSemDobrarTotal() {
        conferir("600");
        multas.receber(motorista.getId(),List.of(multa("400"),multa("200")));
        assertThat(total()).isEqualByComparingTo("600");
    }
    @Test void ausenciaNaoEhZeroEDivergenciaBloqueiaPagamento() {
        conferir(null);
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getPendenciaMultas()).isNotBlank();
        multas.receber(motorista.getId(),List.of(multa("700"))); conferir("600");
        new TransactionTemplate(transactions).executeWithoutResult(s->{var v=viagens.findById(viagem.getId()).orElseThrow(); v.setStatus(StatusViagem.CONCLUIDA);v.setConferencia(Viagem.Conferencia.APROVADA);});
        assertThat(viagens.pagaveis(motorista.getId())).isEmpty();
        assertThat(total()).isEqualByComparingTo("700");
    }
    @Test void entregaRepetidaNaoRefinalizaNemDuplica() {
        viagem.setVtlogJobId("900001"); viagens.save(viagem);
        vtlog.registrarEntrega(entrega("900001","600"));
        var finalizada=viagens.findById(viagem.getId()).orElseThrow().getFinalizadaEm();
        vtlog.registrarEntrega(entrega("900001","600"));
        assertThat(total()).isEqualByComparingTo("600");
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getFinalizadaEm()).isEqualTo(finalizada);
    }

    @Test void totalRecebidoDesbloqueiaEsperaMasNaoOutraDivergencia() {
        viagem.setPendenciaMultas(MultasService.AGUARDANDO); viagens.save(viagem);
        conferir("0");
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getPendenciaMultas()).isNull();
    }

    @Test void entregaRepetidaRespeitaConferenciaManualJaAprovada() {
        multas.receber(motorista.getId(),List.of(multa("700"))); conferir("600");
        multas.confirmar(viagem.getId(), usuarios.findByEmail("admin@lk.com").orElseThrow(), "Valores conferidos com o motorista.");
        conferir("600");
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getPendenciaMultas()).isNull();
        assertThat(total()).isEqualByComparingTo("700");
    }

    @Test void jobAntigoNaoFechaOutraCargaNaMesmaRota() {
        var req=entrega("999888","50");
        var antigo=new VtlogService.EntregaVtlog(req.jobId(),req.steamId(),req.origem(),req.destino(),req.empresaOrigem(),req.empresaDestino(),req.carga(),req.pesoKg(),req.distanciaKm(),req.combustivelGastoL(),req.danoPct(),req.valorFrete(),req.totalMultas(),System.currentTimeMillis()-86400000,System.currentTimeMillis()-86000000);
        assertThatThrownBy(()->vtlog.registrarEntrega(antigo)).isInstanceOf(IllegalStateException.class);
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getStatus()).isEqualTo(StatusViagem.EM_ANDAMENTO);
        assertThat(total()).isZero();
    }
    @Test void eventoEntraAntesDaFinalizacaoNoMesmoPing() {
        var inicial=new TelemetriaPing(); inicial.protocolo=2; inicial.agenteJobId=viagem.getAgenteJobId(); inicial.entregaFeita=false;
        telemetria.registrar(motorista,inicial);
        var fim=new TelemetriaPing(); fim.protocolo=2; fim.agenteJobId=viagem.getAgenteJobId(); fim.entregaFeita=true; fim.multas=List.of(multa("55"));
        telemetria.registrar(motorista,fim);
        assertThat(fim.multasConfirmadas).hasSize(1);
        assertThat(viagens.findById(viagem.getId()).orElseThrow().getStatus()).isEqualTo(StatusViagem.CONCLUIDA);
        assertThat(total()).isEqualByComparingTo("55");
    }
    @Test void novaCargaNaoRecebeMultaDaAnterior() {
        var antigo=multa("70"); viagem.setStatus(StatusViagem.CONCLUIDA); viagens.save(viagem);
        Viagem proxima=novaViagem(); multas.receber(motorista.getId(),List.of(antigo));
        assertThat(total()).isEqualByComparingTo("70");
        assertThat(viagens.findWithEventosById(proxima.getId()).orElseThrow().totalDespesas()).isZero();
    }
}
