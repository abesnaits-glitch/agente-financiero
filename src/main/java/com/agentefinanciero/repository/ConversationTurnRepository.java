package com.agentefinanciero.repository;

import com.agentefinanciero.model.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, UUID> {

    List<ConversationTurn> findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc(
            String usuarioId, String agente);

    long countByUsuarioIdAndAgente(String usuarioId, String agente);

    @Modifying
    @Query(value = """
            DELETE FROM conversation_turns
            WHERE usuario_id = :usuarioId AND agente = :agente
              AND id NOT IN (
                SELECT id FROM conversation_turns
                WHERE usuario_id = :usuarioId AND agente = :agente
                ORDER BY created_at DESC
                LIMIT 20
              )
            """, nativeQuery = true)
    int deleteOldTurns(@Param("usuarioId") String usuarioId,
                       @Param("agente")    String agente);
}
