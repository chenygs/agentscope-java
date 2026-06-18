package io.agentscope.builder.saton.resource.skill.orm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import io.agentscope.builder.saton.resource.skill.orm.entity.SkillRepositoryEntity;

public interface SkillRepositoryRepository extends JpaRepository<SkillRepositoryEntity, Long> {

    List<SkillRepositoryEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<SkillRepositoryEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndName(String ownerId, String name);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
