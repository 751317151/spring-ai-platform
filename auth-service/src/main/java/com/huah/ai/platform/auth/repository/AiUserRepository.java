package com.huah.ai.platform.auth.repository;

import com.huah.ai.platform.auth.model.AiUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AiUserRepository extends JpaRepository<AiUser, String> {
    Optional<AiUser> findByUsername(String username);
    Optional<AiUser> findByUsernameAndEnabledTrue(String username);
}
