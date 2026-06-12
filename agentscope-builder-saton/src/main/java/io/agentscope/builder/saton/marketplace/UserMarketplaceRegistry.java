package io.agentscope.builder.saton.marketplace;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.marketplace.impl.GitBuilderMarketplace;
import io.agentscope.builder.saton.marketplace.impl.NacosBuilderMarketplace;
import io.agentscope.builder.saton.resource.marketplace.SkillMarketplaceEntity;
import io.agentscope.builder.saton.resource.marketplace.SkillMarketplaceRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry mapping DB {@link SkillMarketplaceEntity} records to live
 * {@link BuilderMarketplace} instances. Instances are created on first access
 * and cached per (userId, marketplaceId).
 *
 * <p>Missing classpath deps (git / nacos SDK) at instantiation time are logged
 * and silently skipped — the affected marketplace is unavailable but doesn't
 * block the rest.
 */
@Component
public class UserMarketplaceRegistry {

    private static final Logger log = LoggerFactory.getLogger(UserMarketplaceRegistry.class);

    private final SkillMarketplaceRepository repo;
    private final ConcurrentHashMap<String, Map<String, BuilderMarketplace>> cache = new ConcurrentHashMap<>();

    public UserMarketplaceRegistry(SkillMarketplaceRepository repo) {
        this.repo = repo;
    }

    /** List all available marketplace instances for the given user. */
    public List<BuilderMarketplace> list(String userId) {
        return loadUserCache(userId).values().stream().toList();
    }

    /** Find a specific marketplace instance, or empty if not found / not available. */
    public Optional<BuilderMarketplace> find(String userId, String marketplaceId) {
        return Optional.ofNullable(loadUserCache(userId).get(marketplaceId));
    }

    /** Invalidate (and close) all marketplace instances for a user. Call after CRUD. */
    public void invalidate(String userId) {
        Map<String, BuilderMarketplace> removed = cache.remove(userId);
        if (removed != null) {
            removed.values().forEach(this::closeQuietly);
        }
    }

    /** Invalidate all cached marketplace instances. */
    public void invalidateAll() {
        for (String userId : Set.copyOf(cache.keySet())) {
            invalidate(userId);
        }
    }

    /** Close all instances on shutdown. */
    @PreDestroy
    public void destroy() {
        invalidateAll();
    }

    // -- private helpers --

    private Map<String, BuilderMarketplace> loadUserCache(String userId) {
        return cache.computeIfAbsent(userId, this::loadFromDb);
    }

    private Map<String, BuilderMarketplace> loadFromDb(String userId) {
        List<SkillMarketplaceEntity> entities = repo.findByOwnerIdOrderByCreatedAtDesc(userId);
        Map<String, BuilderMarketplace> result = new LinkedHashMap<>();
        for (SkillMarketplaceEntity entity : entities) {
            try {
                BuilderMarketplace mp = createInstance(entity);
                result.put(entity.getMarketplaceId(), mp);
            } catch (RuntimeException e) {
                log.warn("skip marketplace {} ({}): {}", entity.getMarketplaceId(), entity.getType(), e.getMessage());
            }
        }
        return result;
    }

    private BuilderMarketplace createInstance(SkillMarketplaceEntity entity) {
        String type = entity.getType();
        String propsJson = entity.getPropsJson();
        Map<String, Object> props;
        try {
            props = propsJson != null
                    ? JsonUtil.mapper().readValue(propsJson, new TypeReference<Map<String, Object>>() {})
                    : Map.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid props_json for marketplace " + entity.getMarketplaceId(), e);
        }
        return switch (type) {
            case "git" -> new GitBuilderMarketplace(
                    entity.getMarketplaceId(),
                    stringProp(props, "remoteUrl"),
                    stringProp(props, "branch"),
                    null, // localPath — let GitSkillRepository auto-create temp dir
                    stringProp(props, "skillsRoot"));
            case "nacos" -> new NacosBuilderMarketplace(
                    entity.getMarketplaceId(),
                    stringProp(props, "serverAddr"),
                    stringProp(props, "namespace"),
                    stringProp(props, "username"),
                    stringProp(props, "password"),
                    stringProp(props, "accessKey"),
                    null  // secretKey — optional
            );
            default -> throw new IllegalArgumentException("unsupported marketplace type: " + type);
        };
    }

    private static String stringProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object v = props.get(key);
        return v instanceof String s ? s : null;
    }

    private void closeQuietly(BuilderMarketplace mp) {
        try {
            mp.close();
        } catch (Exception e) {
            log.warn("close marketplace {} ({}) failed", mp.id(), mp.type(), e);
        }
    }
}
