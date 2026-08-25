package com.lktransportes.config;

import com.lktransportes.model.Usuario;
import com.lktransportes.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Cria o primeiro gestor em produção.
 *
 * Sem isso o sistema sobe sem nenhum usuário e ninguém consegue entrar — o seed
 * de desenvolvimento é @Profile("dev") e não roda aqui. Aprovar um cadastro
 * exige um gestor já existente, então o primeiro precisa nascer por fora.
 *
 * Só age quando o banco está vazio: depois de existir qualquer usuário, este
 * runner não faz mais nada, mesmo que as variáveis continuem definidas.
 */
@Configuration
@Profile({"prod", "demo"})
public class PrimeiroGestor {

    private static final Logger log = LoggerFactory.getLogger(PrimeiroGestor.class);

    @Value("${lk.gestor-inicial.email:}")
    private String email;

    @Value("${lk.gestor-inicial.senha:}")
    private String senha;

    @Value("${lk.gestor-inicial.nome:Administrador}")
    private String nome;

    @Bean
    CommandLineRunner criarPrimeiroGestor(UsuarioRepository usuarios) {
        return args -> {
            if (usuarios.count() > 0) return;

            if (email.isBlank() || senha.isBlank()) {
                log.warn("""

                        =====================================================================
                          Nenhum usuário no banco e GESTOR_INICIAL_EMAIL / GESTOR_INICIAL_SENHA
                          não foram definidos. Ninguém consegue entrar no painel.

                          Defina as duas variáveis e reinicie, ou crie o gestor direto no banco.
                        =====================================================================
                        """);
                return;
            }

            Usuario gestor = new Usuario();
            gestor.setNome(nome);
            gestor.setEmail(email);
            gestor.setSenhaHash(new BCryptPasswordEncoder().encode(senha));
            gestor.setPapel(Usuario.Papel.GESTOR);
            gestor.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
            usuarios.save(gestor);

            log.info("""

                    =====================================================================
                      Primeiro gestor criado: {}
                      Troque a senha assim que entrar, e remova GESTOR_INICIAL_SENHA
                      das variáveis de ambiente.
                    =====================================================================
                    """, email);
        };
    }
}
