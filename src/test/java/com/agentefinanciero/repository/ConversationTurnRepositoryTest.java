package com.agentefinanciero.repository;

import com.agentefinanciero.model.ConversationTurn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConversationTurnRepositoryTest {

    @Autowired
    private ConversationTurnRepository repository;

    // Creates a turn with an explicit timestamp so ordering is deterministic in tests.
    private ConversationTurn turn(String userId, ConversationTurn.Role role,
                                   String content, Instant at) {
        ConversationTurn t = new ConversationTurn();
        t.setId(UUID.randomUUID());
        t.setUsuarioId(userId);
        t.setAgente("faro");
        t.setRole(role);
        t.setContent(content);
        t.setCreatedAt(at);
        return t;
    }

    // ── 1. Usuario nuevo retorna lista vacía ──────────────────────────────────

    @Test
    void usuarioNuevo_sinHistorial_retornaListaVacia() {
        List<ConversationTurn> result = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc("usuario_nuevo", "faro");

        assertThat(result).isEmpty();
    }

    // ── 2. Tres intercambios guardan exactamente 6 turns ─────────────────────

    @Test
    void tresIntercambios_guardan6Turns() {
        String userId = "56911111111";
        Instant base = Instant.now();

        for (int i = 0; i < 3; i++) {
            repository.save(turn(userId, ConversationTurn.Role.USER,      "mensaje "   + (i + 1), base.plusSeconds(i * 2)));
            repository.save(turn(userId, ConversationTurn.Role.ASSISTANT, "respuesta " + (i + 1), base.plusSeconds(i * 2 + 1)));
        }

        long total = repository.countByUsuarioIdAndAgente(userId, "faro");
        assertThat(total).isEqualTo(6);
    }

    // ── 3. El historial se carga en orden cronológico (ASC) ──────────────────

    @Test
    void historial_cargaEnOrdenCronologico() {
        String userId = "56922222222";
        Instant base = Instant.now();

        for (int i = 0; i < 3; i++) {
            repository.save(turn(userId, ConversationTurn.Role.USER,      "mensaje "   + (i + 1), base.plusSeconds(i * 2)));
            repository.save(turn(userId, ConversationTurn.Role.ASSISTANT, "respuesta " + (i + 1), base.plusSeconds(i * 2 + 1)));
        }

        List<ConversationTurn> history = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc(userId, "faro");

        assertThat(history).hasSize(6);
        assertThat(history.get(0).getRole()).isEqualTo(ConversationTurn.Role.USER);
        assertThat(history.get(0).getContent()).isEqualTo("mensaje 1");
        assertThat(history.get(5).getRole()).isEqualTo(ConversationTurn.Role.ASSISTANT);
        assertThat(history.get(5).getContent()).isEqualTo("respuesta 3");
    }

    // ── 4. El 4° mensaje carga el contexto de los 3 intercambios anteriores ──

    @Test
    void cuartoMensaje_cargaContextoCompleto() {
        String userId = "56933333333";
        Instant base = Instant.now();

        // 3 exchanges saved (simulating messages 1-3)
        for (int i = 0; i < 3; i++) {
            repository.save(turn(userId, ConversationTurn.Role.USER,      "msg "  + (i + 1), base.plusSeconds(i * 2)));
            repository.save(turn(userId, ConversationTurn.Role.ASSISTANT, "resp " + (i + 1), base.plusSeconds(i * 2 + 1)));
        }

        // 4th message arrives — load context from DB
        List<ConversationTurn> context = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc(userId, "faro");

        // Context must contain all 6 prior turns in chronological order
        assertThat(context).hasSize(6);
        assertThat(context.get(0).getContent()).isEqualTo("msg 1");
        assertThat(context.get(2).getContent()).isEqualTo("msg 2");
        assertThat(context.get(4).getContent()).isEqualTo("msg 3");

        // Roles alternate correctly: USER, ASSISTANT, USER, ASSISTANT...
        for (int i = 0; i < 6; i++) {
            ConversationTurn.Role expected = (i % 2 == 0)
                    ? ConversationTurn.Role.USER
                    : ConversationTurn.Role.ASSISTANT;
            assertThat(context.get(i).getRole()).isEqualTo(expected);
        }
    }

    // ── 5. deleteOldTurns conserva solo los 20 más recientes ─────────────────

    @Test
    void deleteOldTurns_conserva20MasRecientes() {
        String userId = "56944444444";
        Instant base = Instant.now();

        // Save 25 turns (5 beyond the limit of 20)
        for (int i = 0; i < 25; i++) {
            repository.save(turn(userId, ConversationTurn.Role.USER, "msg " + i, base.plusSeconds(i)));
        }

        repository.deleteOldTurns(userId, "faro");

        long remaining = repository.countByUsuarioIdAndAgente(userId, "faro");
        assertThat(remaining).isEqualTo(20);

        // The remaining turns must be the 20 most recent
        List<ConversationTurn> history = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc(userId, "faro");
        assertThat(history.get(0).getContent()).isEqualTo("msg 5");  // oldest kept
        assertThat(history.get(19).getContent()).isEqualTo("msg 24"); // most recent
    }

    // ── 6. Aislamiento entre usuarios ────────────────────────────────────────

    @Test
    void historial_aisladoPorUsuario() {
        Instant base = Instant.now();
        repository.save(turn("56911000001", ConversationTurn.Role.USER, "usuario A", base));
        repository.save(turn("56911000002", ConversationTurn.Role.USER, "usuario B", base));

        List<ConversationTurn> historialA = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc("56911000001", "faro");
        List<ConversationTurn> historialB = repository
                .findTop20ByUsuarioIdAndAgenteOrderByCreatedAtAsc("56911000002", "faro");

        assertThat(historialA).hasSize(1);
        assertThat(historialA.get(0).getContent()).isEqualTo("usuario A");
        assertThat(historialB).hasSize(1);
        assertThat(historialB.get(0).getContent()).isEqualTo("usuario B");
    }
}
