package io.agentscope.builder.saton.auth.orm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import io.agentscope.builder.saton.auth.orm.entity.SysUserEntity;

public interface SysUserRepository extends JpaRepository<SysUserEntity, String> {

    Optional<SysUserEntity> findByUsername(String username);
}
