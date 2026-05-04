package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_turns")
public class ConversationTurn {

    public enum Role { USER, ASSISTANT }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, length = 32)
    private String usuarioId;

    @Column(nullable = false, length = 32)
    private String agente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ConversationTurn() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID    getId()                  { return id; }
    public void    setId(UUID id)           { this.id = id; }

    public String  getUsuarioId()           { return usuarioId; }
    public void    setUsuarioId(String v)   { this.usuarioId = v; }

    public String  getAgente()              { return agente; }
    public void    setAgente(String v)      { this.agente = v; }

    public Role    getRole()                { return role; }
    public void    setRole(Role v)          { this.role = v; }

    public String  getContent()             { return content; }
    public void    setContent(String v)     { this.content = v; }

    public Instant getCreatedAt()           { return createdAt; }
    public void    setCreatedAt(Instant v)  { this.createdAt = v; }
}
