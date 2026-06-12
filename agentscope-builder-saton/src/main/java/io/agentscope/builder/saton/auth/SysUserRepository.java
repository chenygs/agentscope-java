package io.agentscope.builder.saton.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUserEntity, String> {

    Optional<SysUserEntity> findByUsername(String username);
}
