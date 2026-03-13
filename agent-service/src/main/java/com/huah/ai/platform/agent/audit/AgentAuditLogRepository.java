package com.huah.ai.platform.agent.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AiAuditLog, String> {

    List<AiAuditLog> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AiAuditLog> findByAgentTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String agentType, LocalDateTime after);

    @Query("SELECT a.agentType, COUNT(a), AVG(a.latencyMs) FROM AiAuditLog a " +
           "WHERE a.createdAt > :since GROUP BY a.agentType")
    List<Object[]> statsGroupByAgent(LocalDateTime since);

    @Query("SELECT a.userId, SUM(a.promptTokens + a.completionTokens) AS totalTokens " +
           "FROM AiAuditLog a WHERE a.createdAt > :since GROUP BY a.userId ORDER BY totalTokens DESC")
    List<Object[]> topTokenUsersSince(LocalDateTime since);

    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);
}
