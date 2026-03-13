package com.huah.ai.platform.auth.repository;

import com.huah.ai.platform.auth.model.BotPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BotPermissionRepository extends JpaRepository<BotPermission, String> {
    Optional<BotPermission> findByBotTypeAndEnabledTrue(String botType);
}
