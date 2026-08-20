package com.lktransportes.repository;

import com.lktransportes.model.StatusViagem;
import com.lktransportes.model.Viagem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViagemRepository extends JpaRepository<Viagem, UUID> {

    /**
     * @EntityGraph traz eventos e associações na mesma consulta.
     * Sem isso, montar o DTO fora da transação estoura LazyInitializationException
     * (open-in-view está desligado de propósito).
     */
    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao", "carreta"})
    @Query("select distinct v from Viagem v")
    List<Viagem> buscarTodasComEventos();

    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao", "carreta"})
    Optional<Viagem> findWithEventosById(UUID id);

    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao", "carreta"})
    Optional<Viagem> findByMotoristaIdAndStatus(UUID motoristaId, StatusViagem status);

    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao", "carreta"})
    List<Viagem> findByMotoristaIdOrderByCriadaEmDesc(UUID motoristaId);

    @Query("select coalesce(max(v.numero), 2840) from Viagem v")
    Integer ultimoNumero();

    /** Consulta enxuta, sem eventos — usada só para a regra de viagem única. */
    @Query("select v from Viagem v where v.motorista.id = ?1 and v.status = ?2")
    Optional<Viagem> buscarAtivaSimples(UUID motoristaId, StatusViagem status);

    /**
     * Peso já comprometido numa demanda: viagens que ainda não chegaram, mais
     * as que chegaram e estão retidas na conferência.
     *
     * A retida entra aqui de propósito. Ela ainda não abateu a demanda (só abate
     * quando o gestor liberar), então sem esta cláusula a carga sumiria das duas
     * contas ao mesmo tempo — outro motorista pegaria o mesmo saldo e a demanda
     * estouraria a quantidade contratada no dia da liberação.
     */
    @Query("""
           select coalesce(sum(v.pesoKg), 0) from Viagem v
           where v.demanda.id = ?1
             and (v.status <> com.lktransportes.model.StatusViagem.CONCLUIDA
                  or v.conferencia = com.lktransportes.model.Viagem$Conferencia.RETIDA)
           """)
    java.math.BigDecimal pesoEmCursoDaDemanda(UUID demandaId);

    List<Viagem> findByDemandaIdOrderByCriadaEmDesc(UUID demandaId);

    /** Peso em curso de várias demandas de uma vez, pra não fazer N consultas na listagem. */
    @Query("""
           select v.demanda.id, coalesce(sum(v.pesoKg), 0) from Viagem v
           where v.demanda.id in ?1
             and (v.status <> com.lktransportes.model.StatusViagem.CONCLUIDA
                  or v.conferencia = com.lktransportes.model.Viagem$Conferencia.RETIDA)
           group by v.demanda.id
           """)
    List<Object[]> pesoEmCursoPorDemanda(java.util.Collection<UUID> demandaIds);

    /**
     * Viagens que o motorista ainda não fechou — inclui as só CRIADAS.
     * É o que impede um motorista sozinho reservar a demanda inteira.
     */
    @Query("""
           select v from Viagem v
           where v.motorista.id = ?1 and v.status <> com.lktransportes.model.StatusViagem.CONCLUIDA
           """)
    List<Viagem> viagensAbertasDoMotorista(UUID motoristaId);

    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao", "carreta"})
    List<Viagem> findByConferenciaOrderByFinalizadaEmDesc(com.lktransportes.model.Viagem.Conferencia conferencia);

    /**
     * Viagens que já podem virar dinheiro: concluídas, com a conferência
     * resolvida e ainda não incluídas em nenhum acerto.
     */
    @EntityGraph(attributePaths = {"eventos", "motorista", "caminhao"})
    @Query("""
           select v from Viagem v
           where v.status = com.lktransportes.model.StatusViagem.CONCLUIDA
             and v.pagamento is null
             and v.conferencia in (com.lktransportes.model.Viagem$Conferencia.APROVADA,
                                   com.lktransportes.model.Viagem$Conferencia.LIBERADA)
             and (?1 is null or v.motorista.id = ?1)
           order by v.finalizadaEm
           """)
    List<Viagem> pagaveis(UUID motoristaId);
}
