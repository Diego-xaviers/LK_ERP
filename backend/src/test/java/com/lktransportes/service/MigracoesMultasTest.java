package com.lktransportes.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class MigracoesMultasTest {
    @Test void atualizaSchemaExistenteSemReaplicarColunas() throws Exception {
        String url="jdbc:h2:mem:migracao_multas;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("5").load().migrate();
        Flyway novo=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load();
        assertThat(novo.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(novo.migrate().migrationsExecuted).isZero();
        try(var conn=DriverManager.getConnection(url,"sa",""); var st=conn.createStatement()) {
            assertThat(st.executeQuery("select chave_externa, ajuste_vtlog from eventos_viagem").getMetaData().getColumnCount()).isEqualTo(2);
            assertThat(st.executeQuery("select agente_job_id, multas_vtlog, pendencia_multas from viagens").getMetaData().getColumnCount()).isEqualTo(3);
        }
    }
}
