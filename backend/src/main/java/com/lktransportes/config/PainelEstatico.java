package com.lktransportes.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Entrega o painel pelo próprio backend, quando o build do frontend está em
 * `resources/static`.
 *
 * Serve ao modo demo: painel e API saindo do mesmo processo e do mesmo endereço
 * significam um túnel só, nenhum CORS e uma URL só dentro do pacote do agente de
 * telemetria. Na produção com Docker quem entrega o painel é o Caddy, e a pasta
 * static nem existe no build — este controller simplesmente não acha o arquivo e
 * não atrapalha.
 *
 * O que ele resolve é o recarregar: sem isso, dar F5 em `/logistica` devolve 404,
 * porque essa rota só existe dentro do React. Aqui ela vira o index, e o React
 * resolve o resto.
 */
@Controller
@Profile("demo")
public class PainelEstatico {

    /**
     * Só rotas de um nível e sem ponto no nome. Assim `/api/...` e `/actuator/...`
     * (dois níveis) seguem para os controllers de verdade, e `/assets/x.js`
     * (tem ponto) segue para o arquivo estático.
     */
    @RequestMapping({ "/", "/{rota:[^\\.]*}" })
    public String painel() {
        return "forward:/index.html";
    }
}
