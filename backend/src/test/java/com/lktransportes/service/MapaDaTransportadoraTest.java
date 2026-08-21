package com.lktransportes.service;

import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.model.CidadeMapa;
import com.lktransportes.model.MapaConhecido;
import com.lktransportes.model.TelemetriaViagem;
import com.lktransportes.repository.CidadeMapaRepository;
import com.lktransportes.repository.MapaConhecidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A conferência prova que uma viagem aconteceu. O mapa prova que ela aconteceu
 * NO MAPA DA TRANSPORTADORA.
 *
 * O sinal é o id interno da cidade que o jogo reporta (`rbr_sinop`), não o nome:
 * o RBR usa ids próprios, diferentes dos da Europa base e de qualquer outro mod.
 * Nome pode coincidir; id não.
 *
 * Como a lista de cidades não cabe no código — o mod muda, cada servidor roda
 * uma versão — o sistema aprende. Estes testes cobrem as duas metades disso:
 * aprender sem bloquear ninguém, e bloquear depois de trancado.
 */
@SpringBootTest
@ActiveProfiles("dev")
class MapaDaTransportadoraTest {

    @Autowired MapaService mapa;
    @Autowired CidadeMapaRepository cidades;
    @Autowired MapaConhecidoRepository mapas;

    /**
     * O modo do mapa é uma configuração global, única para o sistema inteiro.
     * Um teste que tranca e não destranca faz os seguintes rodarem num mundo
     * diferente do que eles assumem.
     */
    @AfterEach
    void voltarAoAprendizado() {
        mapa.voltarAAprender();
    }

    // ----- aprendendo -----

    @Test
    @DisplayName("aprendendo: a cidade vista entra na lista e nada é bloqueado")
    void aprendendoColetaSemBloquear() {
        mapa.voltarAAprender();
        TelemetriaViagem tv = new TelemetriaViagem();

        mapa.observar(tv, pingDeCidade("rbr_sinop", "Sinop", "rbr_cuiaba", "Cuiabá"));

        assertThat(cidades.findByIdJogo("rbr_sinop")).isPresent();
        assertThat(cidades.findByIdJogo("rbr_cuiaba")).isPresent();

        List<String> motivos = new ArrayList<>();
        mapa.conferir(tv, motivos);
        assertThat(motivos)
                .describedAs("enquanto aprende não há referência para acusar ninguém")
                .isEmpty();
    }

    @Test
    @DisplayName("aprendendo: a mesma cidade não entra duas vezes")
    void aprendendoNaoDuplica() {
        mapa.voltarAAprender();
        TelemetriaViagem tv = new TelemetriaViagem();

        mapa.observar(tv, pingDeCidade("rbr_sorriso", "Sorriso", null, null));
        long depoisDaPrimeira = cidades.count();
        mapa.observar(tv, pingDeCidade("rbr_sorriso", "Sorriso", null, null));

        assertThat(cidades.count()).isEqualTo(depoisDaPrimeira);
    }

    @Test
    @DisplayName("o id do jogo é o que identifica a cidade, não o nome")
    void idEhOQueIdentifica() {
        mapa.voltarAAprender();
        TelemetriaViagem tv = new TelemetriaViagem();

        // Mesmo nome, mapas diferentes: "Sinop" existe no RBR e na Turquia base.
        mapa.observar(tv, pingDeCidade("rbr_sinop", "Sinop", null, null));
        mapa.observar(tv, pingDeCidade("sinop", "Sinop", null, null));

        assertThat(cidades.findByIdJogo("rbr_sinop")).isPresent();
        assertThat(cidades.findByIdJogo("sinop"))
                .describedAs("são cidades diferentes, ainda que o nome bata")
                .isPresent();
    }

    // ----- trancado -----

    @Test
    @DisplayName("trancar exige ter aprendido alguma coisa")
    void trancarExigeCidadeAprendida() {
        cidades.deleteAll();
        mapa.voltarAAprender();

        assertThatThrownBy(() -> mapa.trancar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma cidade foi aprendida");
    }

    @Test
    @DisplayName("trancado: cidade de fora da lista retém a viagem, dizendo qual")
    void trancadoRetemCidadeDeFora() {
        mapa.voltarAAprender();
        TelemetriaViagem aprendizado = new TelemetriaViagem();
        mapa.observar(aprendizado, pingDeCidade("rbr_sinop", "Sinop", "rbr_cuiaba", "Cuiabá"));
        mapa.trancar();

        TelemetriaViagem suspeita = new TelemetriaViagem();
        suspeita.setCidadeOrigemId("paris");
        suspeita.setCidadeDestinoId("rbr_cuiaba");

        List<String> motivos = new ArrayList<>();
        mapa.conferir(suspeita, motivos);

        assertThat(motivos).hasSize(1);
        assertThat(motivos.get(0))
                .contains("origem")
                .contains("paris")
                .contains("não pertence ao mapa da transportadora");
    }

    @Test
    @DisplayName("trancado: cidade conhecida passa")
    void trancadoAceitaCidadeConhecida() {
        mapa.voltarAAprender();
        TelemetriaViagem aprendizado = new TelemetriaViagem();
        mapa.observar(aprendizado, pingDeCidade("rbr_sinop", "Sinop", "rbr_cuiaba", "Cuiabá"));
        mapa.trancar();

        TelemetriaViagem boa = new TelemetriaViagem();
        boa.setCidadeOrigemId("rbr_sinop");
        boa.setCidadeDestinoId("rbr_cuiaba");

        List<String> motivos = new ArrayList<>();
        mapa.conferir(boa, motivos);
        assertThat(motivos).isEmpty();
    }

    @Test
    @DisplayName("trancado, cidade nova do mod NÃO entra sozinha — senão bastaria dirigir fora para ensinar")
    void trancadoNaoAprendeMais() {
        mapa.voltarAAprender();
        TelemetriaViagem tv = new TelemetriaViagem();
        mapa.observar(tv, pingDeCidade("rbr_sinop", "Sinop", null, null));
        mapa.trancar();

        long antes = cidades.count();
        mapa.observar(new TelemetriaViagem(), pingDeCidade("rbr_cidade_nova", "Nova", null, null));

        assertThat(cidades.count())
                .describedAs("aprender de novo é decisão do gestor, não efeito colateral de dirigir")
                .isEqualTo(antes);
        assertThat(cidades.findByIdJogo("rbr_cidade_nova")).isEmpty();
    }

    @Test
    @DisplayName("trancado: sair da área conhecida marca a viagem")
    void trancadoMarcaForaDaArea() {
        mapa.voltarAAprender();
        TelemetriaViagem aprendizado = new TelemetriaViagem();
        mapa.observar(aprendizado, pingDePosicao("rbr_sinop", "Sinop", 0.0, 0.0));
        mapa.trancar();

        TelemetriaViagem longe = new TelemetriaViagem();
        longe.setCidadeOrigemId("rbr_sinop");
        mapa.observar(longe, pingDePosicao("rbr_sinop", "Sinop", 5_000_000.0, 5_000_000.0));

        assertThat(longe.getForaDaArea()).isTrue();

        List<String> motivos = new ArrayList<>();
        mapa.conferir(longe, motivos);
        assertThat(motivos).anyMatch(x -> x.contains("fora da área conhecida"));
    }

    @Test
    @DisplayName("o gestor apaga a cidade que entrou por engano")
    void gestorEsqueceCidade() {
        mapa.voltarAAprender();
        mapa.observar(new TelemetriaViagem(), pingDeCidade("mapa_errado", "Engano", null, null));

        CidadeMapa entrouErrado = cidades.findByIdJogo("mapa_errado").orElseThrow();
        mapa.esquecerCidade(entrouErrado.getId());

        assertThat(cidades.findByIdJogo("mapa_errado")).isEmpty();
    }

    @Test
    @DisplayName("voltar a aprender destrava e passa a coletar de novo")
    void voltarAAprenderDestrava() {
        mapa.voltarAAprender();
        mapa.observar(new TelemetriaViagem(), pingDeCidade("rbr_sinop", "Sinop", null, null));
        mapa.trancar();
        assertThat(mapa.config().getModo()).isEqualTo(MapaConhecido.Modo.ATIVO);

        mapa.voltarAAprender();
        assertThat(mapa.config().getModo()).isEqualTo(MapaConhecido.Modo.APRENDENDO);

        mapa.observar(new TelemetriaViagem(), pingDeCidade("rbr_depois", "Depois", null, null));
        assertThat(cidades.findByIdJogo("rbr_depois")).isPresent();
    }

    // ----- apoio -----

    private TelemetriaPing pingDeCidade(String origemId, String origem, String destinoId, String destino) {
        TelemetriaPing p = new TelemetriaPing();
        p.cidadeOrigemId = origemId;
        p.cidadeOrigem = origem;
        p.cidadeDestinoId = destinoId;
        p.cidadeDestino = destino;
        return p;
    }

    private TelemetriaPing pingDePosicao(String origemId, String origem, double x, double z) {
        TelemetriaPing p = pingDeCidade(origemId, origem, null, null);
        p.posX = x;
        p.posZ = z;
        return p;
    }
}
