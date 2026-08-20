package com.lktransportes.config;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Dados de desenvolvimento — o suficiente pra percorrer o fluxo inteiro. */
@Configuration
@Profile("dev")
public class CargaInicial {

    @Bean
    CommandLineRunner semear(UsuarioRepository usuarios, CaminhaoRepository caminhoes,
                             CarretaRepository carretas, PostoRepository postos,
                             OficinaRepository oficinas, EmpresaParceiraRepository empresas,
                             AvisoRepository avisos, CnhRepository cnhs) {
        return args -> {
            if (usuarios.count() > 0) return;   // só semeia no primeiro boot

            var encoder = new BCryptPasswordEncoder();

            var admin = new Usuario();
            admin.setNome("Diego");
            admin.setEmail("admin@lk.com");
            admin.setSenhaHash(encoder.encode("123456"));
            admin.setPapel(Usuario.Papel.GESTOR);
            admin.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
            usuarios.save(admin);

            var motorista = new Usuario();
            motorista.setNome("Stilnoxgg");
            motorista.setEmail("motorista@lk.com");
            motorista.setSenhaHash(encoder.encode("123456"));
            motorista.setPapel(Usuario.Papel.MOTORISTA);
            motorista.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
            usuarios.save(motorista);

            // CNH já emitida pros dois: sem isso o ambiente de dev nasce travado,
            // porque pegar carga exige carteira válida.
            cnhs.save(cnh(admin));
            cnhs.save(cnh(motorista));

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
            aviso.setTitulo("Comboio da LK — sexta às 21h");
            aviso.setMensagem("Saída de Cuiabá com destino a Sinop. Chegue 10 minutos antes no pátio.");
            aviso.setTipo(Aviso.TipoAviso.EVENTO);
            aviso.setFixado(true);
            avisos.save(aviso);

            var aviso2 = new Aviso();
            aviso2.setTitulo("Novo posto credenciado em Sorriso");
            aviso2.setMensagem("Auto Posto BR-163 já aparece na lista de abastecimento.");
            aviso2.setTipo(Aviso.TipoAviso.INFORMATIVO);
            avisos.save(aviso2);

            System.out.println("""

                =====================================================
                  LK Transportes — dados de desenvolvimento criados
                  admin@lk.com / 123456      (administrador)
                  motorista@lk.com / 123456  (motorista)
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

    private static Cnh cnh(Usuario dono) {
        Cnh c = new Cnh();
        c.setMotorista(dono);
        c.setNumeroRegistro(String.format("%011d", Math.abs(dono.getEmail().hashCode()) % 100000000000L));
        c.setCategoria("E");
        c.setPrimeiraHabilitacao(java.time.LocalDate.now().minusYears(4));
        c.setValidade(java.time.LocalDate.now().plusMonths(3));
        return c;
    }
}
