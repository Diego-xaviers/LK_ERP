package com.lktransportes.config;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cadastros de partida do modo demo: frota, postos, oficinas e empresas.
 *
 * Não cria usuário nenhum, de propósito. O `dev` semeia contas com senha
 * `123456` porque nunca sai da máquina de quem programa; o demo fica exposto na
 * internet, e ali essa conta seria a porta destrancada. O gestor do demo nasce
 * do `PrimeiroGestor`, com a senha que você escolher nas variáveis de ambiente,
 * e os motoristas ele cadastra pelo painel.
 *
 * Semeia só uma vez: com o banco em arquivo, reiniciar o servidor não pode
 * duplicar a frota.
 */
@Configuration
@Profile("demo")
public class CargaDemo {

    @Bean
    CommandLineRunner cadastrosDeDemonstracao(CaminhaoRepository caminhoes, CarretaRepository carretas,
                                              PostoRepository postos, OficinaRepository oficinas,
                                              EmpresaParceiraRepository empresas, AvisoRepository avisos) {
        return args -> {
            if (caminhoes.count() > 0) return;

            caminhoes.save(caminhao("Scania", "R450", "LKT-2A19", "LK-01"));
            caminhoes.save(caminhao("Volvo", "FH 540", "LKT-3B02", "LK-02"));
            caminhoes.save(caminhao("Mercedes", "Actros", "LKT-1C77", "LK-03"));

            carretas.save(carreta("Graneleira", "LKT-9C04", "CR-01"));
            carretas.save(carreta("Baú", "LKT-8B21", "CR-02"));

            postos.save(posto("Posto Trevão", "Sinop"));
            postos.save(posto("Posto Rondonópolis", "Rondonópolis"));
            postos.save(posto("Auto Posto BR-163", "Sorriso"));

            oficinas.save(oficina("Mecânica Trevo", "Sinop"));
            oficinas.save(oficina("Diesel Center", "Cuiabá"));

            empresas.save(empresa("Agro Sinop Cereais", "Agro", "Sinop"));
            empresas.save(empresa("Frigorífico Vale Verde", "Frigorífico", "Cuiabá"));
            empresas.save(empresa("Cooperativa Grão Real", "Agro", "Lucas do Rio Verde"));

            var aviso = new Aviso();
            aviso.setTitulo("Rodada de testes da LK");
            aviso.setMensagem("Ligue o agente de telemetria antes de iniciar a viagem — "
                    + "sem ele a viagem fica retida na conferência e não paga comissão.");
            aviso.setTipo(Aviso.TipoAviso.INFORMATIVO);
            aviso.setFixado(true);
            avisos.save(aviso);

            System.out.println("""

                =====================================================
                  LK Transportes — modo demo
                  Frota, postos, oficinas e empresas cadastrados.
                  Entre com o gestor de GESTOR_INICIAL_EMAIL.
                =====================================================
                """);
        };
    }

    private Caminhao caminhao(String marca, String modelo, String placa, String interno) {
        var c = new Caminhao();
        c.setMarca(marca); c.setModelo(modelo); c.setPlaca(placa); c.setIdentificacaoInterna(interno);
        return c;
    }

    private Carreta carreta(String tipo, String placa, String interno) {
        var c = new Carreta();
        c.setTipo(tipo); c.setPlaca(placa); c.setIdentificacaoInterna(interno);
        return c;
    }

    private Posto posto(String nome, String cidade) {
        var p = new Posto();
        p.setNome(nome); p.setCidade(cidade); p.setEstado("MT");
        return p;
    }

    private Oficina oficina(String nome, String cidade) {
        var o = new Oficina();
        o.setNome(nome); o.setCidade(cidade); o.setEstado("MT");
        return o;
    }

    private EmpresaParceira empresa(String nome, String segmento, String cidade) {
        var e = new EmpresaParceira();
        e.setNome(nome); e.setSegmento(segmento); e.setCidade(cidade); e.setEstado("MT");
        e.setCnpjFicticio("00.000.000/0001-00");
        return e;
    }
}
